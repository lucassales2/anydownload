Unicode true
Name "AnyDownload"
OutFile "@OUT@"
InstallDir "$LOCALAPPDATA\AnyDownload"
RequestExecutionLevel user
SetCompressor zlib

Page directory
Page instfiles
UninstPage uninstConfirm
UninstPage instfiles

Section "Install"
  SetOutPath "$INSTDIR"
  ; `*` keeps extensionless JRE files such as lib\modules. `*.*` would drop them.
  File /r "@IMAGE@\*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\AnyDownload"
  CreateShortcut "$SMPROGRAMS\AnyDownload\AnyDownload.lnk" "$INSTDIR\AnyDownload.exe"
  CreateShortcut "$DESKTOP\AnyDownload.lnk" "$INSTDIR\AnyDownload.exe"
  CreateShortcut "$SMPROGRAMS\AnyDownload\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Uninstall"
  ; App data (%APPDATA%\AnyDownload) and the Downloads folder stay in place.
  Delete "$INSTDIR\Uninstall.exe"
  Delete "$INSTDIR\AnyDownload.exe"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\jre"
  RMDir "$INSTDIR"
  Delete "$SMPROGRAMS\AnyDownload\AnyDownload.lnk"
  Delete "$SMPROGRAMS\AnyDownload\Uninstall.lnk"
  RMDir "$SMPROGRAMS\AnyDownload"
  Delete "$DESKTOP\AnyDownload.lnk"
SectionEnd
