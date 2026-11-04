# Lista M3U actualizada en GitHub

GitHub Actions consulta original_url.json y channels_alt.json de monchotv/MonchoApps
cada seis horas, al cambiar el generador o manualmente desde Actions.
La computadora del usuario no necesita estar encendida.

En un repositorio publico con rama main, la direccion para el reproductor sera:

https://raw.githubusercontent.com/martinreinoso9/lista.m3u/main/canales.m3u

Es una copia actualizada por horario, no una
conversion ejecutada cada vez que el reproductor la consulta. GitHub y el
reproductor pueden guardar una copia temporal.

El workflow debe estar en la rama predeterminada. GitHub puede retrasar las
ejecuciones programadas y desactivarlas tras 60 dias sin actividad en un
repositorio publico. Verificar Actions si estado.txt deja de actualizarse.
Las politicas del repositorio deben permitir Actions y escritura de contenidos
para el token del workflow; no se necesitan claves personales en el codigo.

## Limitaciones

- canales.m3u incluye solo URL completas sin DRM declarado ni tipo WEBVIEW.
- catalogo_con_drm.m3u es un catalogo adicional; no garantiza reproduccion.
- No se exportan claves o licencias DRM ni se renuevan tokens de acceso.
- Las rutas relativas se omiten sin inventar un dominio de origen.
- La reproduccion no fue verificada; puede depender de cuenta, region o permisos.
- Un M3U estatico no interpreta JSON. El generador ejecutado en GitHub lo convierte.
- Un fallo de descarga impide publicar una actualizacion incompleta.

Fuentes:
https://github.com/monchotv/MonchoApps
https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule
