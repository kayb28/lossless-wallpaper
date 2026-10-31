# Wallpaper Sin Pérdida

App Android que conserva intacto el archivo de galería: Recortar aplica sus bytes originales; los modos de composición generan una copia temporal al aplicar.

## Qué hace

- Eliges una imagen de la galería (Photo Picker del sistema, sin permisos de almacenamiento).
- Ves la vista previa con el **recorte exacto a la proporción de tu pantalla** (arrastrable).
- Eliges Recortar, Ajustar, Fondo continuo, Negro, Desenfoque o Extender bordes.
- Ajustar sin recortar ocupa toda la pantalla con la imagen completa; puede estirarla o comprimirla un poco, y la vista previa/aviso muestra el cambio.
- Fondo continuo rellena con una copia ampliada y desenfocada de la misma imagen y desvanece sus bordes para ocultar el límite rectangular.
- Detecta opcionalmente bandas uniformes y simétricas; puedes desactivar la detección o ajustar su sensibilidad.
- Un indicador de calidad compara la resolución de la imagen con la de la pantalla real y con el mínimo que pide el sistema (`getDesiredMinimumWidth/Height`). Si la imagen es menor, lo avisa claramente — **nunca hace upscale a escondidas**.
- Aplicas a pantalla de inicio, de bloqueo o ambas (`FLAG_SYSTEM` / `FLAG_LOCK`).

### La regla de oro (cómo conserva la calidad)

```
ContentResolver.openInputStream(uri)  →  WallpaperManager.setStream(...)
```

En modo **Recortar**, la imagen no pasa por ningún `Bitmap` ni se recomprime: los bytes originales van directo a `WallpaperManager.setStream(...)`. En los otros modos se genera un PNG temporal para aplicar la composición; se borra al terminar y el archivo de galería nunca se modifica.

Si hay que recortar, se comunica un `visibleCropHint` con la proporción exacta de la pantalla. *Nota: algunos launchers ignoran `visibleCropHint` y recortan a su manera; en los launchers de serie (Pixel, One UI, MIUI reciente) suele respetarse.*

### Permisos

Solo `android.permission.SET_WALLPAPER` (permiso *normal*: se concede al instalar, sin diálogos). Nada de almacenamiento, ni red, ni nada más.

## Cómo compilar

**Opción A — GitHub Actions (recomendada):** cada push compila el APK y lo sube como artifact (*Actions → Build APK → Artifacts*). Al crear un tag `v*` se publica además como Release.

**Opción B — local:** JDK 17+ y Gradle 9.8+:

```bash
gradle assembleRelease
# APK en app/build/outputs/apk/release/
```

## Cómo instalar en tu teléfono

1. Descarga el APK del artifact (o del Release si hay tag).
2. Pásalo al teléfono y ábrelo; Android pedirá permitir "instalar apps desconocidas" para esa fuente (normal, no viene de Play Store).
3. Instala. Las actualizaciones firmadas con la misma clave se instalan encima sin desinstalar.

La firma usa un keystore guardado **cifrado en los secrets del repo** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`). No hay claves en ningún archivo. Si algún día quieres firmar localmente con la misma clave, rota los secrets con los tuyos.

## Cómo verificar que la calidad se conserva

1. Elige una imagen con **detalle fino y repetitivo** (texto pequeño, un patrón de líneas finas, una captura de una interfaz). Es donde la recompresión JPEG se ve primero.
2. Aplícala con la app (verifica en el panel que dice "Cobertura completa").
3. Pon la pantalla en el fondo (inicio o bloqueo) y **haz una captura de pantalla**.
4. Compara la captura con el original en el PC haciendo zoom a la misma zona: las líneas deben seguir nítidas, sin los bloques borrosos/"empastados" ni halos que produce una recompresión JPEG agresiva.
5. Prueba de contraste: la misma imagen aplicada con otra app que recompima mostrará artefactos en el zoom; con esta no debería haber diferencia respecto al original (limitado por la resolución de tu pantalla).

## Tests

`gradle test` corre los tests JVM de `CropCalculator` (proporción exacta del recorte, límites del arrastre, detección de imagen menor que la pantalla). También los ejecuta el CI en cada push.

## Roadmap (no incluido aún)

- Fase 2: rotación automática de wallpapers desde una carpeta con WorkManager.
- Fase 2: soporte de wallpapers en vídeo.
