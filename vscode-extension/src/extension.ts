import * as fs from 'fs';
import * as path from 'path';
import { ExtensionContext, window, workspace } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

function resolveServerOptions(context: ExtensionContext): ServerOptions {
    const configuredPath = workspace.getConfiguration('gapl').get<string>('serverPath');

    if (configuredPath && configuredPath.length > 0) {
        // Advanced override: the user is pointing us straight at an executable/script of
        // their own, not a jar - run it directly rather than through `java -jar`.
        return { command: configuredPath, args: [] };
    }

    // The .vsix bundles a prebuilt server jar at server/gapl-lsp.jar (see build.gradle.kts'
    // copyServerJar task) - this is what makes the packaged extension work with no repo clone
    // and no Gradle, only a JDK on PATH. Fall back to the sibling :lsp subproject's shadow-jar
    // build output for local development (`./gradlew :lsp:shadowJar`), which lands in the same
    // shape one directory up.
    const bundledJar = path.join(context.extensionPath, 'server', 'gapl-lsp.jar');
    const devJar = path.join(context.extensionPath, '..', 'lsp', 'build', 'libs', 'gapl-lsp.jar');
    const jarPath = fs.existsSync(bundledJar) ? bundledJar : devJar;

    return { command: 'java', args: ['-jar', jarPath] };
}

export function activate(context: ExtensionContext): void {
    const serverOptions = resolveServerOptions(context);

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'gapl' }],
    };

    client = new LanguageClient('gaplLanguageServer', 'GAPL Language Server', serverOptions, clientOptions);
    client.start().catch((error: unknown) => {
        const message = error instanceof Error ? error.message : String(error);
        if (message.includes('ENOENT')) {
            window.showErrorMessage(
                'GAPL: could not start the language server because "java" was not found. ' +
                'Install a JDK (17+) and make sure it\'s on your PATH, or set "gapl.serverPath" ' +
                'to point at a gapl-lsp executable directly.',
            );
        } else {
            window.showErrorMessage(`GAPL: language server failed to start: ${message}`);
        }
    });
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
