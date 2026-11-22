package com.odisseymc.odisseydungueons.instance;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

/**
 * Handles safe file-level cloning and deletion of Minecraft world instances.
 */
public final class WorldInstanceCloner {

    private WorldInstanceCloner() {}

    /**
     * Copies a source world folder to a target destination folder, omitting uid.dat and session.lock.
     */
    public static boolean copyWorld(File sourceFolder, File targetFolder) {
        if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
            return false;
        }

        try {
            Files.walkFileTree(sourceFolder.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path rel = sourceFolder.toPath().relativize(dir);
                    Path targetDir = targetFolder.toPath().resolve(rel);
                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    // Do not copy lock or uid files to avoid world collision
                    if (name.equalsIgnoreCase("uid.dat") || name.equalsIgnoreCase("session.lock")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = sourceFolder.toPath().relativize(file);
                    Path targetFile = targetFolder.toPath().resolve(rel);
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to copy world folder from " + sourceFolder + " to " + targetFolder, e);
            return false;
        }
    }

    /**
     * Recursively deletes a folder.
     */
    public static boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return true;
        }

        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dirPath, IOException exc) throws IOException {
                    Files.deleteIfExists(dirPath);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to completely delete directory " + dir.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
