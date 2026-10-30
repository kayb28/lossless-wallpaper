# Lossless Wallpaper

App Android mínima que **aplica el archivo de imagen original** como fondo de pantalla, **sin recomprimir ni reescalar** para el paso de aplicación.

## Qué hace

1. Elige una imagen (PNG, WebP o JPG) con el selector de documentos del sistema.
2. Muestra una vista previa con el **recorte a la proporción de la pantalla**.
3. Indica resolución de la imagen vs pantalla y vs `getDesiredMinimumWidth/Height`.
4. Aplica con `WallpaperManager.setStream()` leyendo el `InputStream` del archivo vía `ContentResolver` — **sin pasar por Bitmap en el camino de aplicar**.

## Qué no hace (aún)

- Cambio automático desde una carpeta (WorkManager) — fase 2.
- Wallpapers de vídeo — fase 2.
- Upscale automático (si la imagen es más pequeña, solo avisa).

## Compilar

### GitHub Actions (recomendado)

Cada push a `main` genera un artifact **APK**.

1. Abre la pestaña **Actions** del repo.
2. Entra en el workflow **Build APK**.
3. Descarga el artifact `lossless-wallpaper-apk`.

Un **tag** `v*` publica también un **Release** con el APK.

### Local

```bash
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

(Release de CI se firma con la keystore de debug del runner para poder instalarse; es un APK de uso personal.)

## Instalar en el teléfono

1. Descarga el APK del artifact o del Release.
2. En el teléfono: permitir instalar apps de esa fuente.
3. Abrir el APK e instalar.

No hace falta Android Studio.

## Cómo verificar que la calidad se conserva

1. Elige una imagen con **detalle fino** (texto pequeño, líneas, textura).
2. Aplícala con esta app (Inicio, Bloqueo o ambas).
3. Haz una **captura de pantalla** del home/lock (sin zoom del launcher si puedes).
4. Compara a **100%** (1:1 píxeles) con el archivo original en un visor de PC/móvil:
   - No debería verse el “bloqueo” típico de un JPEG recomprimido otra vez.
   - Si la imagen era **más pequeña** que la pantalla, el sistema puede ampliarla: eso no es recompresión de la app; el aviso de calidad lo indica antes de aplicar.
5. Confirma en el código de aplicación (`WallpaperApplier`) que solo se usa `setStream` + `openInputStream`.

## Permisos

Solo `SET_WALLPAPER`. El acceso a la imagen es el URI que otorga el selector de documentos (sin permiso amplio de almacenamiento).

## Licencia

Uso personal / según el repo.
