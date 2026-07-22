import * as path from 'path';
import { ExtensionContext, workspace } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

function resolveServerCommand(context: ExtensionContext): string {
    const configuredPath = workspace.getConfiguration('gapl').get<string>('serverPath');

    if (configuredPath && configuredPath.length > 0) {
        return configuredPath;
    }

    // Dev-only fallback: assumes this extension is checked out alongside the gapl repo's
    // lsp subproject and `./gradlew :lsp:installDist` has already been run. Real installs
    // should set gapl.serverPath explicitly.
    return path.join(context.extensionPath, '..', 'lsp', 'build', 'install', 'gapl-lsp', 'bin', 'gapl-lsp');
}

export function activate(context: ExtensionContext): void {
    const serverOptions: ServerOptions = {
        command: resolveServerCommand(context),
        args: [],
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'gapl' }],
    };

    client = new LanguageClient('gaplLanguageServer', 'GAPL Language Server', serverOptions, clientOptions);
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
