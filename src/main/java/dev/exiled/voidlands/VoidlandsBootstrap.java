package dev.exiled.voidlands;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Registers the Voidlands biomes before the server loads its worlds. The biomes live in a small
 * datapack bundled in the jar. On startup any of its files missing from plugins/Voidlands/datapack/
 * are copied there, and the pack is loaded from that folder, so admins can edit the fog, particles
 * and sounds and just restart.
 */
public final class VoidlandsBootstrap implements PluginBootstrap {

    private static final String PACK_ID = "voidlands";
    private static final String PACK_RESOURCE = "/voidlands_pack";

    @Override
    public void bootstrap(BootstrapContext context) {
        Path pack = context.getDataDirectory().resolve("datapack");
        try {
            copyMissingFiles(context.getPluginSource(), pack);
        } catch (IOException e) {
            context.getLogger().error("Could not unpack the Voidlands datapack to {}", pack, e);
            return;
        }

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                event.registrar().discoverPack(pack, PACK_ID, configurer -> configurer.autoEnableOnServerStart(true));
            } catch (IOException e) {
                context.getLogger().error("Could not load the Voidlands datapack from {}", pack, e);
            }
        });
    }

    private static void copyMissingFiles(Path pluginJar, Path target) throws IOException {
        try (FileSystem jar = FileSystems.newFileSystem(pluginJar)) {
            Path source = jar.getPath(PACK_RESOURCE);
            List<Path> files;
            try (Stream<Path> walk = Files.walk(source)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                Path destination = target.resolve(source.relativize(file).toString());
                if (Files.notExists(destination)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
    }
}
