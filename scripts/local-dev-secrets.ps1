# Windows-only development helper. SecureStrings are encrypted by DPAPI for this user/PC.
$capLocalSecretPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.local/dev-secrets.xml'
function Read-CapLocalSecrets {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'CAP local credential storage requires Windows DPAPI. No plaintext fallback is available.' }
    if (!(Test-Path -LiteralPath $capLocalSecretPath)) { return @{} }
    try {
        $capValues = Import-Clixml -LiteralPath $capLocalSecretPath
        if ($capValues -isnot [System.Collections.IDictionary]) { throw 'Invalid format' }
        foreach ($capValue in $capValues.Values) { if ($capValue -isnot [Security.SecureString]) { throw 'Invalid credential type' } }
        return $capValues
    } catch { throw 'Saved CAP development credentials could not be decrypted. Run scripts/start-stripe-test.cmd -ForgetCredentials, then start again. No secret values were printed.' }
}
function Save-CapLocalSecret([string]$Name, [Security.SecureString]$Value) {
    $capValues = Read-CapLocalSecrets
    $capValues[$Name] = $Value
    $capDirectory = Split-Path $capLocalSecretPath -Parent
    New-Item -ItemType Directory -Force -Path $capDirectory | Out-Null
    $capTemporary = $capLocalSecretPath + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        New-Item -ItemType File -Path $capTemporary | Out-Null
        $capIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $capAcl = [Security.AccessControl.FileSecurity]::new()
        $capAcl.SetAccessRuleProtection($true,$false)
        $capAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($capIdentity,'FullControl','Allow'))
        Set-Acl -LiteralPath $capTemporary -AclObject $capAcl
        $capValues | Export-Clixml -LiteralPath $capTemporary
        Move-Item -LiteralPath $capTemporary -Destination $capLocalSecretPath -Force
    } catch { throw 'Could not save encrypted CAP development credentials. No plaintext file was written.' }
    finally { if (Test-Path -LiteralPath $capTemporary) { Remove-Item -LiteralPath $capTemporary -Force } }
}
function Get-CapLocalSecret([string]$Name, [string]$Prompt, [string]$Pattern = '', [switch]$Refresh) {
    $capValues = Read-CapLocalSecrets
    if (!$Refresh -and $capValues.Contains($Name)) {
        Write-Host ('Using Windows-encrypted saved credential: ' + $Name)
        return $capValues[$Name]
    }
    while ($true) {
        $capValue = Read-Host $Prompt -AsSecureString
        if (!$Pattern) { return $capValue }
        $capPlain = [System.Net.NetworkCredential]::new('', $capValue).Password.Trim()
        if ($capPlain -cmatch $Pattern) {
            $capValue = ConvertTo-SecureString -String $capPlain -AsPlainText -Force
            $capPlain = $null
            return $capValue
        }
        $capPlain = $null
        Write-Host 'That value has the wrong format. Copy only the requested secret, without quotes. Live keys are not accepted.'
    }
}
function Remove-CapLocalSecrets {
    if (Test-Path -LiteralPath $capLocalSecretPath) { Remove-Item -LiteralPath $capLocalSecretPath -Force }
    Write-Host 'Saved CAP development credentials forgotten. Accounts and database records are unchanged.'
}
