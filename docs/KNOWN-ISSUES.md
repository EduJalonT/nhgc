# Problemas conocidos

Probado con la versión 2026.10.29.004 en dos Chromecast con Google TV (Android 14) y en un televisor
TCL con Android TV 9.

## Google TV (Chromecast)

- **Google TV atiende Inicio por su cuenta.** Ignora la app de inicio predeterminada, así que NHGC
  depende de su servicio de accesibilidad, que recibe la tecla Inicio del mando y abre el launcher.
  Sin ese servicio, Inicio lleva a Google TV.
- **Al arrancar** se ve Google TV unos 2–3 segundos antes de que NHGC tome la pantalla.
- **Justo después de actualizar NHGC** puede quedar Google TV en pantalla hasta pulsar Inicio.
- **Asistente de voz.** Los resultados de búsqueda se muestran dentro de Google TV Home; NHGC los deja
  en pantalla. Si desde ellos se pulsa Atrás hasta la pantalla de inicio de Google TV, esta se queda
  hasta pulsar Inicio.
- **Mantener pulsado Inicio** abre el panel nativo con un temporizador (el filtro de teclas no recibe
  repeticiones); con el mando real aún no se ha comprobado en todos los casos.
- **Teclas enviadas por `adb shell input`** no pasan por el servicio de accesibilidad: para probar
  Inicio o la doble pulsación hay que usar el mando.
- **Salvapantallas del sistema.** Una app no puede iniciarlo en Google TV; el botón de luna abre el
  salvapantallas propio de NHGC (noticias a pantalla completa).
- **Sin el servicio de accesibilidad** la doble pulsación abre los Ajustes de Android en lugar del
  panel. El launcher avisa una vez al día.
- **Nivel 2 (`instalar.ps1 -DesactivarGoogleTV`)** elimina Google TV del todo, pero se pierde el
  panel de ajustes rápidos, que forma parte de Google TV Home.

## Android TV

- **Sin doble pulsación ni vigilante del inicio.** El sistema respeta la app de inicio predeterminada,
  así que NHGC no los necesita y los apaga aunque el servicio esté activado.
- **Entradas.** Algunos televisores con Android TV 9 no anuncian sus entradas de la forma estándar;
  NHGC usa entonces la app de TV del fabricante si está en la lista `live_tv_packages`
  (`launcher/app/src/main/res/values/config.xml`; hoy, la de TCL).

## Ambos

- **Entradas.** Sin entradas propias (Chromecast), el botón y la fila «Tarjetas de entrada» se ocultan.
- **Nombre del dispositivo.** Se lee de `Settings.Global.DEVICE_NAME`; si el sistema no lo da, se
  usa el nombre Bluetooth o el modelo.
