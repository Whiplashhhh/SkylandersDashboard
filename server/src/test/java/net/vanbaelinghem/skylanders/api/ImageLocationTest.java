package net.vanbaelinghem.skylanders.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageLocationTest {
    @TempDir Path workspace;

    @Test
    void findsArtworkFromSharedWorkspaceRepositoryAndServer() throws Exception {
        Path repository = Files.createDirectory(workspace.resolve("SkylandersDashboard"));
        Path images = Files.createDirectory(repository.resolve("images"));
        Path server = Files.createDirectory(repository.resolve("server"));

        for (Path cwd : new Path[] {workspace, repository, server}) {
            assertThat(ImageController.resolveRoot("./images", cwd)).isEqualTo(images);
        }
    }

    @Test
    void keepsExistingLocalDirectoryAndExplicitConfiguration() throws Exception {
        Files.createDirectories(workspace.resolve("SkylandersDashboard/images"));
        Path local = Files.createDirectory(workspace.resolve("images"));
        assertThat(ImageController.resolveRoot("./images", workspace)).isEqualTo(local);
        assertThat(ImageController.resolveRoot("custom-images", workspace))
                .isEqualTo(workspace.resolve("custom-images"));
        Path missing = workspace.resolve("external/images");
        assertThat(ImageController.resolveRoot(missing.toString(), workspace)).isEqualTo(missing);
    }
}
