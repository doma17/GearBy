package cloud.gearby.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GearbyApplication

fun main(args: Array<String>) {
    runApplication<GearbyApplication>(*args)
}
