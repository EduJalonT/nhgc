# Problemas conocidos (Chromecast con Google TV)

Nada de esto se ha validado todavía en un Chromecast real; son límites conocidos por diseño.

- **Destello de Google TV.** Si Google TV recupera la pantalla de inicio, el servicio lo sustituye al
  instante y sin animación, pero puede verse un destello. Desaparece al restablecer la pantalla de
  inicio (aviso del launcher o `reparar-inicio.ps1`).
- **Mantener pulsado Inicio** podría cerrar el panel nativo solo mientras Google TV tenga recuperada
  la pantalla de inicio. La doble pulsación de Inicio está protegida en todos los casos.
- **Diálogo de app de inicio.** No se ha comprobado si Google TV ofrece el diálogo oficial para
  elegir la app de inicio. Si no lo ofrece, el aviso indica el comando ADB.
- **Botón Entradas.** Envía `KEYCODE_TV_INPUT`, como el botón del mando. Si el mando cambia de entrada
  por infrarrojos directamente a la TV, el Chromecast no puede imitarlo (no tiene emisor de
  infrarrojos).
- **Nombre del dispositivo.** Se lee de `Settings.Global.DEVICE_NAME`; si el sistema no lo da, se
  usa el nombre Bluetooth o el modelo.
- **Sin el servicio de accesibilidad** la doble pulsación abre los Ajustes de Android en lugar del
  panel, y la pantalla de inicio no se vigila. El launcher avisa una vez al día.
- **Nivel 2 (`instalar.ps1 -DesactivarGoogleTV`)** elimina el destello del todo, pero se pierde el
  panel de ajustes rápidos, que forma parte de Google TV Home.
