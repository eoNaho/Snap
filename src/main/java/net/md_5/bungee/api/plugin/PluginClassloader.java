/*
 * Copyright (c) 2012, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * You may not use the software for commercial software hosting services without
 * written permission from the author.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.md_5.bungee.api.plugin;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.ProxyServer;

final class PluginClassloader extends URLClassLoader {

    private static final Logger LOGGER = Logger.getLogger(PluginClassloader.class.getName());
    private static final Set<PluginClassloader> allLoaders = new CopyOnWriteArraySet<>();

    private final ProxyServer proxy;
    private final PluginDescription desc;
    private final JarFile jar;
    private final Manifest manifest;
    private final URL url;
    private final ClassLoader libraryLoader;

    private Plugin plugin;

    public PluginClassloader(ProxyServer proxy, PluginDescription desc, File file, ClassLoader libraryLoader) throws IOException {
        super(new URL[]{file.toURI().toURL()}, proxy.getClass().getClassLoader());
        this.proxy = proxy;
        this.desc = desc;
        this.jar = new JarFile(file);
        this.manifest = jar.getManifest();
        this.url = file.toURI().toURL();
        this.libraryLoader = libraryLoader;
        allLoaders.add(this);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass0(name, resolve, true);
    }

    private Class<?> loadClass0(String name, boolean resolve, boolean checkOther) throws ClassNotFoundException {
        // 1. Tentar carregar da própria classloader
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException ignored) {}

        // 2. Tentar carregar das bibliotecas
        if (libraryLoader != null) {
            try {
                return libraryLoader.loadClass(name);
            } catch (ClassNotFoundException ignored) {}
        }

        // 3. Tentar carregar de outros plugins com dependências
        if (checkOther) {
            for (PluginClassloader loader : allLoaders) {
                if (loader != this && proxy.getPluginManager().isTransitiveDepend(desc, loader.desc)) {
                    try {
                        return loader.loadClass0(name, resolve, false);
                    } catch (ClassNotFoundException ignored) {}
                }
            }
        }

        throw new ClassNotFoundException("Class " + name + " not found for plugin " + desc.getName());
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        JarEntry entry = jar.getJarEntry(path);

        if (entry == null) {
            return super.findClass(name);
        }

        try (InputStream is = jar.getInputStream(entry)) {
            byte[] classBytes = ByteStreams.toByteArray(is);
            definePackageIfNeeded(name);

            CodeSource source = new CodeSource(url, entry.getCodeSigners());
            return defineClass(name, classBytes, 0, classBytes.length, source);
        } catch (IOException ex) {
            throw new ClassNotFoundException(name, ex);
        }
    }

    private void definePackageIfNeeded(String className) {
        int dotIndex = className.lastIndexOf('.');
        if (dotIndex == -1) return;

        String pkgName = className.substring(0, dotIndex);
        if (getPackage(pkgName) == null) {
            try {
                if (manifest != null) {
                    definePackage(pkgName, manifest, url);
                } else {
                    definePackage(pkgName, null, null, null, null, null, null, null);
                }
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Failed to define package {0} for plugin {1}",
                        new Object[]{pkgName, desc.getName()});
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            jar.close();
            allLoaders.remove(this);
            LOGGER.log(Level.FINE, "Closed classloader for plugin {0}", desc.getName());
        }
    }

    void init(Plugin plugin) {
        Preconditions.checkArgument(plugin != null, "Plugin cannot be null");
        Preconditions.checkArgument(plugin.getClass().getClassLoader() == this,
                "Invalid ClassLoader for plugin %s", desc.getName());

        if (this.plugin != null) {
            throw new IllegalStateException("Plugin already initialized: " + desc.getName());
        }

        this.plugin = plugin;
        plugin.init(proxy, desc);
        LOGGER.log(Level.INFO, "Initialized plugin {0} v{1}",
                new Object[]{desc.getName(), desc.getVersion()});
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}