package com.fpspatcher.init;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Automatically discovers mixin classes under the configured mixin package.
 */
public class AutoDiscoveryMixinPlugin implements IMixinConfigPlugin {
    private static final List<AutoDiscoveryMixinPlugin> mixinPlugins = new ArrayList<AutoDiscoveryMixinPlugin>();

    public static List<AutoDiscoveryMixinPlugin> getMixinPlugins() {
        return mixinPlugins;
    }

    private String mixinPackage;
    private List<String> mixins = null;

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
        mixinPlugins.add(this);
    }

    public URL getBaseUrlForClassUrl(URL classUrl) {
        String string = classUrl.toString();
        if (classUrl.getProtocol().equals("jar")) {
            try {
                return new URL(string.substring(4).split("!")[0]);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        if (string.endsWith(".class")) {
            try {
                return new URL(string.replace("\\", "/")
                        .replace(getClass().getCanonicalName()
                                .replace(".", "/") + ".class", ""));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return classUrl;
    }

    public String getMixinPackage() {
        return mixinPackage;
    }

    public String getMixinBaseDir() {
        return mixinPackage.replace(".", "/");
    }

    public void tryAddMixinClass(String className) {
        String norm = (className.endsWith(".class") ? className.substring(0, className.length() - ".class".length()) : className)
                .replace("\\", "/")
                .replace("/", ".");
        if (norm.contains("$")) {
            return;
        }
        if (norm.startsWith(getMixinPackage() + ".") && !norm.endsWith(".")) {
            mixins.add(norm.substring(getMixinPackage().length() + 1));
        }
    }

    @Override
    public List<String> getMixins() {
        if (mixins != null) {
            return mixins;
        }
        mixins = new ArrayList<String>();
        URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        Path file;
        try {
            file = Paths.get(getBaseUrlForClassUrl(classUrl).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (Files.isDirectory(file)) {
            walkDir(file);
        } else {
            walkJar(file);
        }
        return mixins;
    }

    private void walkDir(Path classRoot) {
        try {
            Stream<Path> classes = Files.walk(classRoot.resolve(getMixinBaseDir()));
            try {
                classes.map(it -> classRoot.relativize(it).toString())
                        .forEach(this::tryAddMixinClass);
            } finally {
                classes.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void walkJar(Path file) {
        try {
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(file));
            try {
                ZipEntry next;
                while ((next = zis.getNextEntry()) != null) {
                    tryAddMixinClass(next.getName());
                    zis.closeEntry();
                }
            } finally {
                zis.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }
}
