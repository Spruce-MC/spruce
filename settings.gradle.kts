rootProject.name = "spruce"

include("spruce-api")
include("spruce-core")
include("spruce-loader")
include("spruce-processor")
include("spruce-proto")
include("spruce-gateway")
include("spruce-loader:spruce-loader-spigot")
findProject(":spruce-loader:spruce-loader-spigot")?.name = "spruce-loader-spigot"
include("spruce-loader:spruce-loader-commons")
findProject(":spruce-loader:spruce-loader-commons")?.name = "spruce-loader-commons"
include("spruce-loader:spruce-loader-velocity")
findProject(":spruce-loader:spruce-loader-velocity")?.name = "spruce-loader-velocity"
include("spruce-loader:spruce-loader-velocity")
findProject(":spruce-loader:spruce-loader-velocity")?.name = "spruce-loader-velocity"
include("spruce-processor:spruce-processor-commons")
findProject(":spruce-processor:spruce-processor-commons")?.name = "spruce-processor-commons"
include("spruce-processor:spruce-processor-spigot")
findProject(":spruce-processor:spruce-processor-spigot")?.name = "spruce-processor-spigot"
include("spruce-processor:spruce-processor-velocity")
findProject(":spruce-processor:spruce-processor-velocity")?.name = "spruce-processor-velocity"
include("spruce-processor:spruce-processor-models")
findProject(":spruce-processor:spruce-processor-models")?.name = "spruce-processor-models"
include("spruce-processor:spruce-processor-service")
findProject(":spruce-processor:spruce-processor-service")?.name = "spruce-processor-service"
include("spruce-processor:spruce-processor-plugins")
findProject(":spruce-processor:spruce-processor-plugins")?.name = "spruce-processor-plugins"
include("spruce-loader:spruce-loader-plugins")
findProject(":spruce-loader:spruce-loader-plugins")?.name = "spruce-loader-plugins"
include("spruce-loader:spruce-loader-service")
findProject(":spruce-loader:spruce-loader-service")?.name = "spruce-loader-service"
