param([switch]$Servir, [int]$Puerto=8787)
$ErrorActionPreference='Stop'
$base='https://raw.githubusercontent.com/monchotv/MonchoApps/main/'
$dest=$PSScriptRoot
function Texto($v) { return ([string]$v -replace '[\r\n\x00-\x1f]',' ' -replace '"',"'").Trim() }
function Generar {
    $items=[System.Collections.Generic.List[object]]::new()
    # Si falla cualquier fuente, conservar los archivos anteriores.
    foreach($fuente in @('channels_alt.json','original_url.json')) {
        $data=Invoke-RestMethod -Uri ($base+$fuente) -TimeoutSec 40
        if(-not $data.categories) { throw "Formato inesperado en $fuente" }
        foreach($cat in $data.categories) {
            foreach($c in $cat.samples) {
                $url=Texto $c.original_url
                $uri=$null
                $completa=[Uri]::TryCreate($url,[UriKind]::Absolute,[ref]$uri) -and $uri.Scheme -in @('http','https')
                $drm=([bool]$c.drm_license_uri -or ([string]$c.type -match 'CLEARKEY|WIDEVINE|PLAYREADY|DRM'))
                $items.Add([pscustomobject]@{Nombre=(Texto $c.name);Categoria=(Texto $cat.name);Logo=(Texto $c.icono);URL=$url;Tipo=(Texto $c.type);DRM=$drm;Completa=$completa;Fuente=$fuente;Headers=$c.headers})
            }
        }
    }
    if($items.Count -eq 0) { throw 'Las fuentes no contienen canales; se conserva la lista anterior.' }
    $seen=@{}
    $lista=[System.Collections.Generic.List[string]]::new()
    $catalogo=[System.Collections.Generic.List[string]]::new()
    $lista.Add('#EXTM3U')
    $catalogo.Add('#EXTM3U')
    $informe=[System.Collections.Generic.List[object]]::new()
    $n=0; $total=0
    foreach($c in $items) {
        $key=$c.Nombre+'|'+$c.URL+'|'+$c.Tipo+'|'+($c.Headers | ConvertTo-Json -Compress)
        if($seen.ContainsKey($key)) { continue }; $seen[$key]=$true
        $web=($c.Tipo -eq 'WEBVIEW')
        $estado=if(-not $c.Completa){'URL incompleta: omitido'}elseif($web){'WEBVIEW: pagina web, solo catalogo'}elseif($c.DRM){'DRM: solo catalogo, requiere acceso autorizado'}else{'Lista general: reproduccion sin verificar'}
        $informe.Add([pscustomobject]@{Nombre=$c.Nombre;Categoria=$c.Categoria;Tipo=$c.Tipo;Estado=$estado;Fuente=$c.Fuente;URL=$c.URL})
        if(-not $c.Completa) { continue }
        $nombre=$c.Nombre
        if($c.DRM){$nombre+=' [DRM]'}
        if($web){$nombre+=' [WEBVIEW]'}
        $linea='#EXTINF:-1 tvg-logo="'+$c.Logo+'" group-title="'+$c.Categoria+'",'+$nombre
        $bloque=[System.Collections.Generic.List[string]]::new()
        $bloque.Add($linea)
        if($c.Headers) {
            foreach($par in @(@('User-Agent','http-user-agent'),@('Referer','http-referrer'),@('Origin','http-origin'))) {
                $v=Texto $c.Headers.($par[0])
                if($v){$bloque.Add('#EXTVLCOPT:'+ $par[1]+'='+$v)}
            }
        }
        $bloque.Add($c.URL)
        $catalogo.AddRange($bloque); $total++
        if(-not $c.DRM -and -not $web){$lista.AddRange($bloque);$n++}
    }
    $utf8=[System.Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllLines((Join-Path $dest 'canales.m3u'),$lista,$utf8)
    [IO.File]::WriteAllLines((Join-Path $dest 'catalogo_con_drm.m3u'),$catalogo,$utf8)
    $informe | Export-Csv (Join-Path $dest 'informe.csv') -NoTypeInformation -Encoding UTF8
    $resumen="Actualizado: $([DateTimeOffset]::Now.ToString('o'))`r`nRegistros fuente: $($items.Count)`r`nEntradas unicas: $($informe.Count)`r`nLista general: $n`r`nCatalogo con URL completa: $total`r`nNo se verifico reproduccion. No se exportaron claves ni licencias DRM."
    [IO.File]::WriteAllText((Join-Path $dest 'estado.txt'),$resumen,$utf8)
    Write-Host $resumen
}
if(-not $Servir) { try { Generar } catch { Write-Error "No se pudo actualizar: $_"; exit 1 }; exit }
$listener=[Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$Puerto/")
$listener.Start()
Write-Host "Deja esta ventana abierta. URL: http://localhost:$Puerto/canales.m3u"
try {
    while($listener.IsListening) {
        $ctx=$listener.GetContext()
        try {
            if($ctx.Request.Url.AbsolutePath -ne '/canales.m3u'){$ctx.Response.StatusCode=404;continue}
            Generar
            $bytes=[IO.File]::ReadAllBytes((Join-Path $dest 'canales.m3u'))
            $ctx.Response.ContentType='audio/x-mpegurl; charset=utf-8'
            $ctx.Response.Headers.Add('Cache-Control','no-store')
            $ctx.Response.ContentLength64=$bytes.Length
            $ctx.Response.OutputStream.Write($bytes,0,$bytes.Length)
        } catch {
            $ctx.Response.StatusCode=502
            Write-Warning "Error al consultar las fuentes: $_"
        } finally { $ctx.Response.Close() }
    }
} finally {$listener.Stop();$listener.Close()}
