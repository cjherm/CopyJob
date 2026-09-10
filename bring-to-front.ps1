Add-Type -Namespace CopyJobLauncher -Name Native -MemberDefinition '
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
[DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
'

$stopwatch = [Diagnostics.Stopwatch]::StartNew()
while ($stopwatch.Elapsed.TotalSeconds -lt 120) {
    $proc = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq 'CopyJob' } |
        Sort-Object StartTime -Descending |
        Select-Object -First 1

    if ($proc) {
        $handle = $proc.MainWindowHandle
        $SWP_NOMOVE_NOSIZE = 0x0003
        [CopyJobLauncher.Native]::ShowWindow($handle, 9) | Out-Null           # SW_RESTORE
        [CopyJobLauncher.Native]::SetWindowPos($handle, [IntPtr]-1, 0, 0, 0, 0, $SWP_NOMOVE_NOSIZE) | Out-Null   # HWND_TOPMOST
        Start-Sleep -Milliseconds 150
        [CopyJobLauncher.Native]::SetWindowPos($handle, [IntPtr]-2, 0, 0, 0, 0, $SWP_NOMOVE_NOSIZE) | Out-Null   # HWND_NOTOPMOST
        [CopyJobLauncher.Native]::SetForegroundWindow($handle) | Out-Null
        break
    }

    Start-Sleep -Milliseconds 300
}
