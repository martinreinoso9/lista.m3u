# Moncho Player

Aplicación Android propia para celular y TV, basada en el catálogo público
de monchotv/MonchoApps. No es una copia binaria de PlayTV ni una app oficial de Flow.

Incluye categorías, búsqueda, favoritos, reproductor Media3 HLS/DASH, pantalla
ampliada, actualización manual y copia local del catálogo para fallos de red.
Consulta ambos JSON directamente. Ante HTTP 401/403 vuelve a consultar una vez
el catálogo y prueba una URL distinta publicada para el mismo nombre y categoría.
No obtiene tokens privados ni reconstruye firmas de PlayTV.

Muestra todos los canales. Las entradas DRM y WEBVIEW y las rutas incompletas
se identifican; no se extraen claves ni se inventa un dominio. No hay integración
de licencias de proveedor configurada: las entradas DRM no reproducen en esta versión.
Los encabezados publicados User-Agent, Origin y Referer se aplican a cada señal.

Únicos permisos: INTERNET y ACCESS_NETWORK_STATE. No hay instalador, servicio
en segundo plano, receptor de arranque ni descarga/actualización de APK.
Al salir de la app se libera el reproductor. Para instalar una versión nueva,
el usuario debe hacerlo manualmente. Las URLs HTTP publicadas necesitan tráfico
sin cifrar habilitado; no se ignoran errores de certificados TLS.

Compilar con Java 17, Android SDK 35 y Gradle 8.9:
`gradle --no-daemon assembleDebug lintDebug`

El resultado es app/build/outputs/apk/debug/app-debug.apk.
La compilación y el análisis de permisos se ejecutan también en GitHub Actions.
No se garantiza la disponibilidad de las señales publicadas por terceros.
