package cloud.gearby.api.catalog

import cloud.gearby.api.catalog.application.command.CorrectionRuleCommand
import cloud.gearby.api.catalog.application.command.FeedbackResolveCommand
import cloud.gearby.api.catalog.application.command.FeedbackSubmitCommand
import cloud.gearby.api.catalog.application.command.ManualCategoryReviewFlagCommand
import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.application.service.CatalogService
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CategoryReviewFlagSource
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.FeedbackKind
import cloud.gearby.api.catalog.domain.FeedbackResolutionStatus
import cloud.gearby.api.catalog.domain.NotificationStatus
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.support.PostgresIntegrationTest
import cloud.gearby.api.support.TestAuthentication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(CatalogWorkflowIntegrationTest.FixedClockConfiguration::class)
@TestPropertySource(properties = ["gearby.catalog.review-period=PT24H"])
class CatalogWorkflowIntegrationTest
    @Autowired
    constructor(
        private val catalog: CatalogService,
        private val mockMvc: MockMvc,
        private val jdbc: NamedParameterJdbcTemplate,
    ) : PostgresIntegrationTest() {
        @BeforeEach
        fun resetMutableData() {
            jdbc.update("DELETE FROM feedback_notification_attempts", emptyMap<String, Any>())
            jdbc.update("DELETE FROM category_review_flags", emptyMap<String, Any>())
            jdbc.update("DELETE FROM feedback", emptyMap<String, Any>())
            jdbc.update("DELETE FROM audit_events", emptyMap<String, Any>())
            val seedIds =
                listOf(UUID.fromString("11111111-1111-1111-1111-111111111111"), UUID.fromString("22222222-2222-2222-2222-222222222222"))
            jdbc.update(
                "DELETE FROM correction_rules WHERE id <> :seed",
                mapOf(
                    "seed" to UUID.fromString("33333333-3333-3333-3333-333333333333"),
                ),
            )
            jdbc.update("DELETE FROM store_categories WHERE store_id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
            jdbc.update("DELETE FROM stores WHERE id NOT IN (:seedIds)", mapOf("seedIds" to seedIds))
        }

        @Test
        fun `Flyway seeds reviewed categories and lifecycle writes audit data`() {
            assertEquals(Category.entries.toSet(), catalog.categories().toSet())
            val draft =
                catalog.create(
                    StoreUpsertCommand("Test Store", "Seoul", Coordinates(BigDecimal("37.5"), BigDecimal("127.0")), setOf(Category.HIKING)),
                    "test-admin",
                )
            assertEquals(StoreStatus.DRAFT, draft.status)
            assertEquals(StoreStatus.IN_REVIEW, catalog.transition(draft.id, StoreStatus.IN_REVIEW, "test-admin")?.status)
            assertEquals(StoreStatus.PUBLISHED, catalog.transition(draft.id, StoreStatus.PUBLISHED, "test-admin")?.status)
            assertNotNull(catalog.published().singleOrNull { it.id == draft.id })
            assertEquals(listOf("CREATE_DRAFT", "IN_REVIEW", "PUBLISHED"), catalog.auditEvents(draft.id).map { it.action })
        }

        @Test
        fun `reviewed store becomes publicly discoverable and its feedback can be resolved`() {
            val draft =
                catalog.create(
                    StoreUpsertCommand("Flow Store", "Seoul", Coordinates(BigDecimal("37.5"), BigDecimal("127.0")), setOf(Category.HIKING)),
                    "test-admin",
                )
            catalog.transition(draft.id, StoreStatus.IN_REVIEW, "test-admin")
            catalog.transition(draft.id, StoreStatus.PUBLISHED, "test-admin")

            mockMvc.get("/api/v1/stores") { param("q", "Flow Store") }.andExpect {
                status { isOk() }
                jsonPath("$.data.items[0].id") { value(draft.id.toString()) }
            }
            val receipt =
                catalog.submitFeedback(
                    FeedbackSubmitCommand(FeedbackKind.GENERAL, draft.id, "Please update hours", "admin@example.test", true),
                )
            val resolved =
                catalog.resolveFeedback(
                    receipt.id,
                    FeedbackResolveCommand(FeedbackResolutionStatus.RESOLVED, "Hours verified"),
                    "test-admin",
                )

            assertEquals(FeedbackResolutionStatus.RESOLVED, resolved?.resolutionStatus)
            assertEquals(NotificationStatus.QUEUED, resolved?.notificationStatus)
            assertEquals("RESOLVED_FEEDBACK", catalog.auditEvents(receipt.id).last().action)
        }

        @Test
        fun `admin boundary fails closed without OIDC configuration`() {
            mockMvc.get("/api/v1/admin/stores").andExpect {
                status { isUnauthorized() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
            mockMvc
                .patch("/api/v1/admin/feedback/11111111-1111-1111-1111-111111111111") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"resolutionStatus":"RESOLVED","resolutionSummary":"Handled"}"""
                }.andExpect { status { isUnauthorized() } }
            mockMvc.get("/api/v1/categories").andExpect { status { isOk() } }
            mockMvc.get("/actuator/health").andExpect { status { isOk() } }
            mockMvc.get("/api/v1/unmapped").andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `public discovery filters published stores and discloses corrections`() {
            val draft =
                catalog.create(
                    StoreUpsertCommand(
                        "Hidden draft",
                        "Seoul",
                        Coordinates(BigDecimal("37.5"), BigDecimal("127.0")),
                        setOf(Category.BACKPACKING),
                    ),
                    "test-admin",
                )
            val published =
                catalog.create(
                    StoreUpsertCommand(
                        "Backpacking House",
                        "Seoul",
                        Coordinates(BigDecimal("37.57"), BigDecimal("126.98")),
                        setOf(Category.BACKPACKING),
                    ),
                    "test-admin",
                )
            catalog.transition(published.id, StoreStatus.IN_REVIEW, "test-admin")
            catalog.transition(published.id, StoreStatus.PUBLISHED, "test-admin")

            mockMvc.get("/api/v1/stores") { param("category", "BACKPACKING") }.andExpect {
                status { isOk() }
                jsonPath("$.data.items.length()") { value(1) }
                jsonPath("$.data.items[0].id") { value(published.id.toString()) }
            }
            mockMvc.get("/api/v1/stores") { param("q", "백패킨") }.andExpect {
                status { isOk() }
                jsonPath("$.data.search.originalQuery") { value("백패킨") }
                jsonPath("$.data.search.appliedQuery") { value("BACKPACKING") }
                jsonPath("$.data.search.correction") { value("백패킨 → BACKPACKING") }
                jsonPath("$.data.items[0].id") { value(published.id.toString()) }
            }
            mockMvc
                .get("/api/v1/stores") {
                    param("q", "백패킨")
                    param("applyCorrection", "false")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.search.originalQuery") { value("백패킨") }
                    jsonPath("$.data.search.appliedQuery") { value("백패킨") }
                    jsonPath("$.data.search.correction") { value(null) }
                    jsonPath("$.data.items.length()") { value(0) }
                }
            mockMvc.get("/api/v1/stores/${draft.id}").andExpect {
                status { isNotFound() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("NOT_FOUND") }
            }
        }

        @Test
        fun `freshness changes at the fixed clock boundary without hiding published stores`() {
            val storeId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            mapOf(
                FIXED_NOW.minus(REVIEW_PERIOD).plusSeconds(1) to "VERIFIED",
                FIXED_NOW.minus(REVIEW_PERIOD) to "REVIEW_DUE",
                FIXED_NOW.minus(REVIEW_PERIOD).minusSeconds(1) to "REVIEW_DUE",
            ).forEach { (verifiedAt, expectedStatus) ->
                jdbc.update(
                    "UPDATE stores SET verified_at = :verifiedAt WHERE id = :id",
                    mapOf("verifiedAt" to Timestamp.from(verifiedAt), "id" to storeId),
                )

                mockMvc.get("/api/v1/stores/$storeId").andExpect {
                    status { isOk() }
                    jsonPath("$.data.verifiedAt") { value(verifiedAt.toString()) }
                    jsonPath("$.data.informationStatus") { value(expectedStatus) }
                }
            }
        }

        @Test
        fun `admin freshness follows lifecycle while public responses remain published only`() {
            val admin = TestAuthentication.admin()
            mockMvc
                .post("/api/v1/admin/stores") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "name":"Lifecycle freshness store",
                          "address":"Seoul",
                          "coordinates":{"latitude":37.5,"longitude":127.0},
                          "categories":["HIKING"]
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.status") { value("DRAFT") }
                    jsonPath("$.data.verifiedAt") { value(null) }
                    jsonPath("$.data.informationStatus") { value(null) }
                }
            val draftId = catalog.findByStatus(StoreStatus.DRAFT).single { it.name == "Lifecycle freshness store" }.id

            mockMvc.get("/api/v1/stores/$draftId").andExpect { status { isNotFound() } }
            mockMvc.post("/api/v1/admin/stores/$draftId/review") { with(admin) }.andExpect { status { isOk() } }
            mockMvc.post("/api/v1/admin/stores/$draftId/publish") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("PUBLISHED") }
                jsonPath("$.data.verifiedAt") { value(FIXED_NOW.toString()) }
                jsonPath("$.data.informationStatus") { value("VERIFIED") }
            }
            mockMvc.get("/api/v1/stores/$draftId").andExpect {
                status { isOk() }
                jsonPath("$.data.verifiedAt") { value(FIXED_NOW.toString()) }
                jsonPath("$.data.informationStatus") { value("VERIFIED") }
            }
            jdbc.update(
                "UPDATE stores SET verified_at = :verifiedAt WHERE id = :id",
                mapOf("verifiedAt" to Timestamp.from(FIXED_NOW.minus(REVIEW_PERIOD)), "id" to draftId),
            )
            mockMvc.post("/api/v1/admin/stores/$draftId/publish") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.verifiedAt") { value(FIXED_NOW.toString()) }
                jsonPath("$.data.informationStatus") { value("VERIFIED") }
            }
        }

        @Test
        fun `admin operations expose protected queue health rules and resolution state`() {
            val feedbackId =
                catalog
                    .submitFeedback(
                        FeedbackSubmitCommand(
                            FeedbackKind.GENERAL,
                            content = "Please update hours",
                            replyEmail = "admin@example.com",
                            contactConsent = true,
                        ),
                    ).id
            catalog.createCorrectionRule(CorrectionRuleCommand("campin", CorrectionTargetType.CATEGORY, "CAMPING"))

            val admin = TestAuthentication.admin()
            mockMvc.get("/api/v1/admin/dashboard") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data.activeCorrectionRules") { value(2) }
                jsonPath("$.data.categoryHealth.length()") { value(4) }
            }
            mockMvc.get("/api/v1/admin/correction-rules") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.source == 'campin')]") { exists() }
            }
            mockMvc.get("/api/v1/admin/feedback") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data[0].replyEmail") { doesNotExist() }
            }
            mockMvc
                .patch("/api/v1/admin/feedback/$feedbackId") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"resolutionStatus":"RESOLVED","resolutionSummary":"Hours reviewed"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.resolutionStatus") { value("RESOLVED") }
                    jsonPath("$.data.notificationStatus") { value("QUEUED") }
                }
            mockMvc.get("/api/v1/admin/dashboard") { with(TestAuthentication.user()) }.andExpect {
                status { isForbidden() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("FORBIDDEN") }
            }
        }

        @Test
        fun `admin can update and delete correction rules with audit events`() {
            val rule = catalog.createCorrectionRule(CorrectionRuleCommand("campnig", CorrectionTargetType.CATEGORY, "CAMPING"))
            val admin = TestAuthentication.admin()

            mockMvc
                .patch("/api/v1/admin/correction-rules/${rule.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"source":"camping gear","targetType":"STORE","target":"Gear House","active":false}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.source") { value("camping gear") }
                    jsonPath("$.data.targetType") { value("STORE") }
                    jsonPath("$.data.active") { value(false) }
                }
            assertEquals(1, catalog.dashboard().activeCorrectionRules)
            mockMvc
                .patch("/api/v1/admin/correction-rules/${rule.id}") {
                    with(TestAuthentication.user())
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"source":"camping gear","targetType":"STORE","target":"Gear House","active":true}"""
                }.andExpect { status { isForbidden() } }
            mockMvc
                .delete("/api/v1/admin/correction-rules/${rule.id}") { with(admin) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.success") { value(true) }
                    jsonPath("$.data") { value(null) }
                }

            assertEquals(
                listOf("UPDATE_CORRECTION_RULE", "DELETE_CORRECTION_RULE"),
                catalog.auditEvents(rule.id).map { it.action }.takeLast(2),
            )
        }

        @Test
        fun `public discovery validates location sort and accepts feedback`() {
            mockMvc.get("/api/v1/stores") { param("sort", "distance") }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith("application/json") }
                jsonPath("$.success") { value(false) }
                jsonPath("$.timestamp") { exists() }
                jsonPath("$.data") { value(null) }
                jsonPath("$.error.code") { value("INVALID_REQUEST") }
            }
            mockMvc
                .get("/api/v1/stores") {
                    param("sort", "distance")
                    param("near", "37.5665,126.9780")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].id") { value("11111111-1111-1111-1111-111111111111") }
                }
            mockMvc
                .post("/api/v1/feedback") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"kind":"GENERAL","content":"Please update hours"}"""
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.data.status") { value("ACCEPTED") }
                    jsonPath("$.data.id") { exists() }
                }
        }

        @Test
        fun `feedback resolution records notification state only for consented contact`() {
            val receipt =
                catalog.submitFeedback(
                    FeedbackSubmitCommand(
                        FeedbackKind.GENERAL,
                        content = "Please update hours",
                        replyEmail = "admin@example.test",
                        contactConsent = true,
                    ),
                )

            val resolved =
                catalog.resolveFeedback(
                    receipt.id,
                    FeedbackResolveCommand(FeedbackResolutionStatus.RESOLVED, "Hours verified"),
                    "test-admin",
                )

            assertEquals(FeedbackResolutionStatus.RESOLVED, resolved?.resolutionStatus)
            assertEquals(NotificationStatus.QUEUED, resolved?.notificationStatus)
            assertEquals("Hours verified", resolved?.resolutionSummary)
            assertEquals(1, catalog.notificationAttempts(receipt.id))
            assertEquals(
                null,
                catalog.resolveFeedback(
                    receipt.id,
                    FeedbackResolveCommand(FeedbackResolutionStatus.REJECTED, "Already handled"),
                    "test-admin",
                ),
            )
            assertEquals(1, catalog.notificationAttempts(receipt.id))
            assertEquals("RESOLVED_FEEDBACK", catalog.auditEvents(receipt.id).last().action)
            val rejected = catalog.submitFeedback(FeedbackSubmitCommand(FeedbackKind.GENERAL, content = "Not applicable"))
            catalog.resolveFeedback(rejected.id, FeedbackResolveCommand(FeedbackResolutionStatus.REJECTED, "Out of scope"), "test-admin")
            assertEquals("REJECTED_FEEDBACK", catalog.auditEvents(rejected.id).last().action)
        }

        @Test
        fun `feedback resolution rejects pending or blank input without audit`() {
            val receipt = catalog.submitFeedback(FeedbackSubmitCommand(FeedbackKind.GENERAL, content = "Please update hours"))
            val admin = TestAuthentication.admin()
            mockMvc
                .patch("/api/v1/admin/feedback/${receipt.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"resolutionStatus":"PENDING","resolutionSummary":"No"}"""
                }.andExpect { status { isBadRequest() } }
            mockMvc
                .patch("/api/v1/admin/feedback/${receipt.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"resolutionStatus":"RESOLVED","resolutionSummary":"  "}"""
                }.andExpect { status { isBadRequest() } }
            assertEquals(FeedbackResolutionStatus.PENDING, catalog.feedback().single { it.id == receipt.id }.resolutionStatus)
            assertEquals(emptyList(), catalog.auditEvents(receipt.id))
        }

        @Test
        fun `category health and flags are protected and auditable`() {
            val store = catalog.published().first()
            val admin = TestAuthentication.admin()
            mockMvc
                .post("/api/v1/admin/category-review-flags") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"storeId":"${store.id}","reason":"Category needs review"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.storeId") { value(store.id.toString()) }
                    jsonPath("$.data.storeName") { value(store.name) }
                    jsonPath("$.data.source") { value("MANUAL") }
                    jsonPath("$.data.sourceFeedbackId") { value(null) }
                    jsonPath("$.data.assignee") { value(null) }
                    jsonPath("$.data.resolution") { value(null) }
                    jsonPath("$.data.createdAt") { exists() }
                    jsonPath("$.data.resolvedAt") { value(null) }
                    jsonPath("$.data.resolvedBy") { value(null) }
                }
            val flag = catalog.categoryReviewFlags().single()
            mockMvc.get("/api/v1/admin/category-health") { with(admin) }.andExpect {
                status { isOk() }
                jsonPath("$.data[?(@.openReviewFlagCount == 1)]") { exists() }
            }
            mockMvc
                .patch("/api/v1/admin/category-review-flags/${flag.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"state":"RESOLVED"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.state") { value("RESOLVED") }
                }
            assertEquals("UPDATE_CATEGORY_REVIEW_FLAG", catalog.auditEvents(flag.id).last().action)
        }

        @Test
        fun `manual category flags require store and bounded text`() {
            val store = catalog.published().first()
            val admin = TestAuthentication.admin()
            mockMvc
                .post("/api/v1/admin/category-review-flags") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"reason":"General category review"}"""
                }.andExpect { status { isBadRequest() } }
            mockMvc
                .post("/api/v1/admin/category-review-flags") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"storeId":"${store.id}","reason":"${"x".repeat(501)}"}"""
                }.andExpect { status { isBadRequest() } }
            val flag =
                catalog.createManualCategoryReviewFlag(
                    ManualCategoryReviewFlagCommand(store.id, "Category needs review"),
                    "test-admin",
                )
            mockMvc
                .patch("/api/v1/admin/category-review-flags/${flag.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"assignee":"${"x".repeat(201)}"}"""
                }.andExpect { status { isBadRequest() } }
            mockMvc
                .patch("/api/v1/admin/category-review-flags/${flag.id}") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"resolution":"${"x".repeat(1001)}"}"""
                }.andExpect { status { isBadRequest() } }
        }

        @Test
        fun `published stores cannot be edited directly`() {
            val admin = TestAuthentication.admin()

            mockMvc
                .patch("/api/v1/admin/stores/11111111-1111-1111-1111-111111111111") {
                    with(admin)
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content =
                        """{"name":"Changed public store","address":"Seoul","coordinates":{"latitude":37.5,"longitude":127.0},"categories":["HIKING"]}"""
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.success") { value(false) }
                    jsonPath("$.error.code") { value("CONFLICT") }
                }

            assertEquals("GearBy Seoul Trail", catalog.find(UUID.fromString("11111111-1111-1111-1111-111111111111"))?.name)
        }

        @Test
        fun `category flags require explicit feedback input and can be raised from rejection reasons`() {
            val store = catalog.published().first()
            catalog.submitFeedback(FeedbackSubmitCommand(FeedbackKind.CORRECTION, store.id, "Wrong phone"))
            assertEquals(0, catalog.categoryReviewFlags().size)

            catalog.submitFeedback(FeedbackSubmitCommand(FeedbackKind.GENERAL, store.id, "Wrong category", categoryRelated = true))
            assertEquals(CategoryReviewFlagSource.FEEDBACK, catalog.categoryReviewFlags().single().source)

            val draft =
                catalog.create(
                    StoreUpsertCommand(
                        "Review store",
                        "Seoul",
                        Coordinates(BigDecimal("37.5"), BigDecimal("127.0")),
                        setOf(Category.HIKING),
                    ),
                    "test-admin",
                )
            catalog.transition(draft.id, StoreStatus.IN_REVIEW, "test-admin")
            catalog.transition(draft.id, StoreStatus.REJECTED, "test-admin", "Category is wrong")

            assertEquals(CategoryReviewFlagSource.REJECTION, catalog.categoryReviewFlags(storeId = draft.id).single().source)
        }

        @TestConfiguration(proxyBeanMethods = false)
        class FixedClockConfiguration {
            @Bean
            @Primary
            fun clock(): Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        }

        private companion object {
            val FIXED_NOW: Instant = Instant.parse("2026-01-02T03:04:05Z")
            val REVIEW_PERIOD: Duration = Duration.ofHours(24)
        }
    }
