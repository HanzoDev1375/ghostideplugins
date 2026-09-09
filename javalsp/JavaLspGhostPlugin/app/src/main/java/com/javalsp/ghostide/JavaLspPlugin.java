package com.javalsp.ghostide;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ir.hanzodev1375.ghostide.ide.api.EditorExtensionPoints;
import ir.hanzodev1375.ghostide.ide.ui.api.IdeHostServices;
import ir.hanzodev1375.ghostide.ide.ui.api.ProotProcessLauncher;
import ir.hanzodev1375.ghostide.plugin.api.Disposable;
import ir.hanzodev1375.ghostide.plugin.api.GhostPlugin;
import ir.hanzodev1375.ghostide.plugin.api.PluginContext;
import ir.hanzodev1375.ghostide.plugin.api.PluginSetupAction;

/**
 * Installs Node.js (apt fallback) and {@code jj-language-server} (Julien Dubois's pure-JavaScript
 * Java LSP, github.com/jdubois/jj-language-server) into the proot rootfs. The server speaks LSP
 * over stdio ({@code jj-language-server --stdio}) using {@code vscode-languageserver} and parses
 * Java with {@code java-parser} (Chevrotain) - it needs no JVM, only Node. On-device footprint is
 * ~15 MB versus ~64 MB + JVM for Eclipse JDT LS.
 *
 * <p>The installer shell script lives in {@code assets/install-java-lsp.sh} inside the .gpl package
 * and is read at runtime through the plugin's own Android Context; we never duplicate it as a Java
 * string constant. {@code RawCommand} embeds that script into a single heredoc and executes it -
 * the same as running {@code bash install.sh} by hand.
 */
public final class JavaLspPlugin implements GhostPlugin {

  private static final String INSTALL_SCRIPT_ASSET = "install-java-lsp.sh";
  private static final String INSTALL_DIR = "/opt/java-lsp";

  private PluginContext context;

  @Override
  public List<PluginSetupAction> getSetupActions() {
    if (context == null) {
      return List.of();
    }
    String body = readAsset(context, INSTALL_SCRIPT_ASSET);
    if (body.isBlank()) {
      return List.of();
    }
    String command =
        "mkdir -p "
            + INSTALL_DIR
            + "\n"
            + "cat > "
            + INSTALL_DIR
            + "/install.sh <<'INSTALL_EOF'\n"
            + body
            + "\nINSTALL_EOF\n"
            + "chmod +x "
            + INSTALL_DIR
            + "/install.sh\n"
            + INSTALL_DIR
            + "/install.sh\n";
    return List.of(
        new PluginSetupAction(
            "install-java-language-server",
            "Install Java language server (jj-language-server)",
            command,
            "Installs Node.js (if missing or <18) and jj-language-server - Julien Dubois's "
                + "pure-TypeScript Java LSP - into the proot rootfs via npm (default registry, "
                + "npmmirror.com as fallback). No JVM is needed. Provides completions, hover, "
                + "diagnostics, document symbols, formatting, code actions, references, rename "
                + "and more for .java files."));
  }

  @Override
  public void activate(PluginContext context) {
    this.context = context;
    ProotProcessLauncher launcher =
        context.getServices().require(IdeHostServices.PROOT_PROCESS_LAUNCHER);
    JavaLspProvider provider = new JavaLspProvider(launcher);
    Disposable registration =
        context
            .getExtensions()
            .register(
                EditorExtensionPoints.LSP_SERVER_PROVIDER,
                provider,
                context.getDescriptor().getId(),
                0);
    context.registerDisposable(registration);
    context
        .getLogger()
        .info(
            provider.isInstalled()
                ? "jj-language-server found in rootfs"
                : "jj-language-server not installed yet; run the setup action from the Plugin Manager");
  }

  private static String readAsset(PluginContext context, String name) {
    Context appContext = context.getServices().require(IdeHostServices.PLUGIN_ANDROID_CONTEXT);
    try (InputStream in = appContext.getAssets().open(name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      context.getLogger().error("Failed to read asset: " + name, e);
      return "";
    }
  }
}
