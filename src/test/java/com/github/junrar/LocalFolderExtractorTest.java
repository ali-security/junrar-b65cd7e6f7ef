package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFolderExtractorTest {
    private static File tempFolder;

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");

        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops/");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException2() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "parent-dir.rar");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempFolder);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            File expectedInvalidPath = new File(tempFolder.getParentFile().getParentFile(), "tmp");
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath.toString());
        }
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException3() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "sibling-prefix-traversal.rar");

        // the archive entry is "../extract_evil/pwned.txt": a sibling of the destination sharing its name as prefix
        File tempDir = new File(tempFolder, "extract");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempDir);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            File expectedInvalidPath = new File(new File(tempFolder, "extract_evil"), "pwned.txt");
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath.getCanonicalPath());
            assertThat(expectedInvalidPath).doesNotExist();
        }
    }

    @Test
    public void rarWithSiblingPrefixTraversal_ExtractedThroughJunrar_ShouldNotWriteOutsideTarget() throws Exception {
        File root = new File(tempFolder, "sibling-prefix");
        File destination = new File(root, "extract");
        assertThat(destination.mkdirs()).isTrue();
        File file = TestCommons.writeResourceToFolder(root, "sibling-prefix-traversal.rar");

        // the archive entry is "../extract_evil/pwned.txt", which resolves to <root>/extract_evil/pwned.txt
        File escapedDir = new File(root, "extract_evil");
        File escapedFile = new File(escapedDir, "pwned.txt");

        Throwable thrown = catchThrowable(() -> Junrar.extract(file, destination));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(escapedFile.getCanonicalPath());
        assertThat(escapedFile).doesNotExist();
        assertThat(escapedDir).doesNotExist();
    }

    @Test
    public void rarWithDirectoryInSiblingWithSamePrefix_ShouldThrowException() throws IOException {
        File root = TestCommons.createTempDir();
        File destination = new File(root, "extract");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(destination);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../extract_evil/");

        File expectedInvalidPath = new File(root, "extract_evil");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains invalid path")
            .containsIgnoringCase(expectedInvalidPath.getCanonicalPath());
        FileUtils.deleteDirectory(root);
    }

    @Test
    public void rarWithFileInSiblingWithSamePrefix_ShouldThrowException() throws IOException {
        File root = TestCommons.createTempDir();
        File destination = new File(root, "extract");
        assertThat(destination.mkdirs()).isTrue();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(destination);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("..\\extract_evil\\pwned.txt");

        File escapedFile = new File(new File(root, "extract_evil"), "pwned.txt");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(escapedFile.getCanonicalPath());
        assertThat(escapedFile).doesNotExist();
        FileUtils.deleteDirectory(root);
    }

    @Test
    public void createDirectory_WithFilesystemRootAsDestination_ShouldAllowEntriesInsideRoot() throws IOException {
        File fsRoot = File.listRoots()[0];
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(fsRoot);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("junrar-dir/");

        File directory = localFolderExtractor.createDirectory(fileHeader);

        assertThat(directory).isNotNull();
        assertThat(directory.getCanonicalPath()).startsWith(fsRoot.getCanonicalPath());
    }

    @Test
    public void canonicalPathWithTrailingSeparator_ShouldEndWithSingleSeparator() throws IOException {
        File destination = new File(tempFolder, "extract");
        assertThat(LocalFolderExtractor.canonicalPathWithTrailingSeparator(destination))
            .isEqualTo(destination.getCanonicalPath() + File.separator);

        File fsRoot = File.listRoots()[0];
        assertThat(LocalFolderExtractor.canonicalPathWithTrailingSeparator(fsRoot))
            .isEqualTo(fsRoot.getCanonicalPath())
            .endsWith(File.separator);
    }

    @Test
    public void rarWithBackslashTraversal_ExtractedThroughJunrar_ShouldNotWriteOutsideTarget() throws Exception {
        File root = new File(tempFolder, "public-api");
        File destination = new File(root, "a" + File.separator + "b");
        assertThat(destination.mkdirs()).isTrue();
        File file = TestCommons.writeResourceToFolder(root, "parent-dir.rar");

        // the archive entry is "..\..\tmp\existing-file", which resolves to <root>/tmp/existing-file
        File escapedFile = new File(new File(root, "tmp"), "existing-file");

        Throwable thrown = catchThrowable(() -> Junrar.extract(file, destination));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(escapedFile.getCanonicalPath());
        assertThat(escapedFile).doesNotExist();
    }

    @Test
    public void rarWithMixedSeparatorsTraversal_ShouldThrowException() throws IOException {
        File root = new File(tempFolder, "mixed-separators");
        File destination = new File(root, "a" + File.separator + "b");
        assertThat(destination.mkdirs()).isTrue();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(destination);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("..\\../escaped");

        File escapedFile = new File(root, "escaped");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(escapedFile.getCanonicalPath());
        assertThat(escapedFile).doesNotExist();
    }

    @Test
    public void invariantSeparatorsPathString_ShouldReplaceBackslashes() {
        assertThat(LocalFolderExtractor.invariantSeparatorsPathString("..\\..\\tmp\\file")).isEqualTo("../../tmp/file");
        assertThat(LocalFolderExtractor.invariantSeparatorsPathString("dir/sub\\file")).isEqualTo("dir/sub/file");
        assertThat(LocalFolderExtractor.invariantSeparatorsPathString("file")).isEqualTo("file");
    }
}
