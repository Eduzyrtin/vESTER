# Google Play: vESTER

Paquete `com.sanabria.vester` · móvil y tableta · ficha en [`fastlane/metadata/android/`](../fastlane/metadata/android)
(formato estándar, ver §8 de `HERRAMIENTAS/GUIA_PROYECTOS.md`). Idiomas: los 9 de la app; `en-US` es el predeterminado.

- **Precio**: gratuita. También se descarga gratis desde GitHub (Releases).

## Qué subir y dónde (Play Console › Crecimiento › Presencia en la tienda › Ficha principal)

1. **Idiomas**: Gestionar idiomas → añade `es-ES`, `fr-FR`, `de-DE`, `it-IT`, `pt-PT`, `ca`, `eu-ES`, `gl-ES`. Pega en cada uno
   `title.txt`, `short_description.txt` y `full_description.txt` de su carpeta.
2. **Icono**: `en-US/images/icon.png`.
3. **Gráfico de funciones** (obligatorio): `en-US/images/featureGraphic.png` (vale para todos los idiomas).
4. **Capturas de teléfono**: las de `en-US/images/phoneScreenshots/` (4; 1080×1920). Valen para todos los idiomas.
5. **Novedades** de cada versión: `<idioma>/changelogs/<versionCode>.txt` (la 2.0 es el `versionCode` 21).

## Antes de publicar (lo que Play pedirá)

- **Política de privacidad (URL)**: obligatoria.
- **Seguridad de los datos**: la app no tiene permiso de internet; ningún dato sale del dispositivo salvo lo que el usuario
  exporta él mismo (CSV, PDF, copia de seguridad en la carpeta que elige). Se puede declarar «no se recogen datos».
- **Acceso a notificaciones** (`NotificationListenerService`, opcional): Play revisa este uso. Justificación: sugerir
  movimientos a partir de notificaciones de pago de una lista corta de apps de banco, cartera y correo, que el usuario
  confirma; no se guarda ni se envía nada. La descripción ya lo explica con estas palabras.
- **Clasificación de contenido**, **público objetivo** y **anuncios** (ninguno). Categoría: Finanzas.
