#define UNICODE
#define _UNICODE
#include <windows.h>
#include <strsafe.h>

/*
 * Starts the bundled Temurin runtime with app\AnyDownload.jar.
 * The process inherits the user PATH, so a machine without yt-dlp still
 * launches; the app reports the missing tool instead of this launcher.
 */

static void fail(const wchar_t *message) {
    MessageBoxW(NULL, message, L"AnyDownload", MB_OK | MB_ICONERROR);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, LPWSTR commandLine, int show) {
    wchar_t modulePath[32768];
    wchar_t javaw[32768];
    wchar_t jar[32768];
    wchar_t cmdline[32768];
    DWORD length;
    wchar_t *slash;
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;

    (void)instance;
    (void)previous;
    (void)commandLine;
    (void)show;

    length = GetModuleFileNameW(NULL, modulePath, 32768);
    if (length == 0 || length >= 32768) {
        fail(L"AnyDownload could not find its own folder.");
        return 1;
    }
    slash = wcsrchr(modulePath, L'\\');
    if (slash == NULL) {
        fail(L"AnyDownload could not find its own folder.");
        return 1;
    }
    *slash = L'\0';

    if (FAILED(StringCchPrintfW(javaw, 32768, L"%s\\jre\\bin\\javaw.exe", modulePath)) ||
        FAILED(StringCchPrintfW(jar, 32768, L"%s\\app\\AnyDownload.jar", modulePath))) {
        fail(L"The install path is too long.");
        return 1;
    }
    if (GetFileAttributesW(javaw) == INVALID_FILE_ATTRIBUTES) {
        fail(L"The bundled Java runtime is missing. Reinstall AnyDownload.");
        return 1;
    }
    if (GetFileAttributesW(jar) == INVALID_FILE_ATTRIBUTES) {
        fail(L"The application files are missing. Reinstall AnyDownload.");
        return 1;
    }
    if (FAILED(StringCchPrintfW(cmdline, 32768, L"\"%s\" -jar \"%s\"", javaw, jar))) {
        fail(L"The install path is too long.");
        return 1;
    }

    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    ZeroMemory(&process, sizeof(process));
    /* NULL environment inherits PATH. Do not replace it with the JRE bin. */
    if (!CreateProcessW(javaw, cmdline, NULL, NULL, FALSE, 0, NULL, modulePath, &startup, &process)) {
        fail(L"AnyDownload could not start.");
        return 1;
    }
    CloseHandle(process.hProcess);
    CloseHandle(process.hThread);
    return 0;
}
