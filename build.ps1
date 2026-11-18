param(
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }),
    [string]$KeyStore,
    [switch]$DebugBuild,
    [string]$BuildDir = (Join-Path $PSScriptRoot 'build-local')
)
$ErrorActionPreference='Stop'
function RunTool([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Tool failed ($LASTEXITCODE): $Exe" }
}
$env:JAVA_HOME=$Jdk
$env:PATH=(Join-Path $Jdk 'bin')+';'+$env:PATH
$platform=Get-ChildItem (Join-Path $Sdk 'platforms') -Directory | Where-Object Name -match '^android-\d+(\.\d+)?$' | Sort-Object { [double]($_.Name.Substring(8)) } -Descending | Select-Object -First 1
$android=Join-Path $platform.FullName 'android.jar'
$bt=(Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory | Where-Object Name -match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
$main=Join-Path $PSScriptRoot 'app\src\main'
# Each invocation gets isolated intermediates; the signing key persists across builds.
New-Item -ItemType Directory $BuildDir -Force | Out-Null
$run=Join-Path $BuildDir ([Guid]::NewGuid().ToString('N'))
foreach($sub in 'gen','classes','dex'){New-Item -ItemType Directory (Join-Path $run $sub) -Force | Out-Null}
if(!$KeyStore){$KeyStore=Join-Path $BuildDir 'local-debug.jks'}
if(!(Test-Path -LiteralPath $KeyStore)){
    RunTool "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$KeyStore,'-storepass','android','-keypass','android','-alias','tailer','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Tailer Local Build')
}
RunTool "$bt\aapt2.exe" @('compile','--dir',"$main\res",'-o',"$run\resources.zip")
$linkArgs=@('link','-I',$android,'--manifest',"$main\AndroidManifest.xml",'-A',"$main\assets",'--java',"$run\gen",'-o',"$run\base.apk","$run\resources.zip")
if($DebugBuild){$linkArgs+='--debug-mode'}
RunTool "$bt\aapt2.exe" $linkArgs
$sources=@(Get-ChildItem "$main\java","$run\gen" -Recurse -Filter '*.java' | ForEach-Object FullName)
RunTool "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',("$bt\core-lambda-stubs.jar;"+$android),'-d',"$run\classes")+$sources)
RunTool "$Jdk\bin\jar.exe" @('cf',"$run\classes.jar",'-C',"$run\classes",'.')
RunTool "$bt\d8.bat" @('--min-api','29','--lib',$android,'--output',"$run\dex","$run\classes.jar")
RunTool "$Jdk\bin\jar.exe" @('uf',"$run\base.apk",'-C',"$run\dex",'classes.dex')
RunTool "$bt\zipalign.exe" @('-f','-p','4',"$run\base.apk","$run\aligned.apk")
$apk=Join-Path $BuildDir 'Tailer.apk'
RunTool "$bt\apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$run\aligned.apk")
RunTool "$bt\apksigner.bat" @('verify','--verbose',$apk)
Write-Output $apk
