param(
    [ValidateSet('Inspect','Backup','Migrate','RestoreVerify','DropTest')][string]$Mode='Inspect',
    [string]$ConfigPath='railway/Cachchay.md',
    [string]$BackupFolder='backups/coupon-db-backup',
    [string]$JavaPath='D:\.tools_web\jdk-17.0.20.1+1\bin\java.exe',
    [string]$ConnectorJar=(Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository\com\mysql\mysql-connector-j\9.1.0\mysql-connector-j-9.1.0.jar')
)
$ErrorActionPreference='Stop'
# Extract values, never execute scripts from a credential document or print its contents.
$couponSettings=Get-Content -LiteralPath $ConfigPath -Raw
$couponOldValues=@{}
try {
    foreach($couponKey in @('MYSQL_HOST','MYSQL_PORT','MYSQL_USER','MYSQL_PASSWORD','MYSQL_DATABASE')) {
        $couponOldValues[$couponKey]=[Environment]::GetEnvironmentVariable($couponKey,'Process')
        $couponMatch=[regex]::Match($couponSettings, ('(?m)\$env:'+$couponKey+'\s*=\s*["'']([^"''\r\n]+)["'']'))
        if(-not $couponMatch.Success) { $couponMatch=[regex]::Match($couponSettings, ('(?m)^'+$couponKey+'\s*=\s*["'']?([^"''\r\n]+?)["'']?\s*$')) }
        if($couponMatch.Success) { [Environment]::SetEnvironmentVariable($couponKey,$couponMatch.Groups[1].Value,'Process') }
    }
    & $JavaPath --class-path $ConnectorJar (Join-Path $PSScriptRoot 'CouponDatabaseMaintenance.java') $Mode $BackupFolder
    if($LASTEXITCODE -ne 0) { throw 'Database operation did not complete. No deployment should proceed.' }
} finally {
    foreach($couponKey in $couponOldValues.Keys) { [Environment]::SetEnvironmentVariable($couponKey,$couponOldValues[$couponKey],'Process') }
}
