# kotlinx.serialization keeps its generated serializers via its own consumer rules.
# Keep the accessibility service and tile services (referenced from the manifest only).
-keep class io.github.kuscher.discobar.bar.BarService { *; }
