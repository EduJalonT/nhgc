# New Home Google Chromecast (NHGC)

Launcher ligero para **Chromecast con Google TV**, basado en
[CleanYourTV / tv-vibehack](https://github.com/Gathin23/cleanyourtv) (licencia MIT).

Sustituye a Google TV Home como pantalla de inicio sin desactivarlo. Google TV Home sigue instalado
como mecanismo de recuperación y conserva su panel de ajustes rápidos.

> Estado: compila, pasa los tests unitarios y el lint, pero **aún no se ha validado en un Chromecast
> real**. Consulta [docs/KNOWN-ISSUES.md](docs/KNOWN-ISSUES.md) antes de instalarlo.

## Qué hace

| Función | Cómo se usa |
|---|---|
| Opciones del launcher | Botón con icono de lápiz en la barra superior (el mando del Chromecast no tiene tecla Menu) |
| Ajustes rápidos del sistema | Pulsar **Inicio dos veces** (menos de 0,5 s) abre el panel nativo de Google TV |
| Pantalla de inicio | El launcher queda como inicio predeterminado; si Google TV la recupera, lo detecta y ofrece restablecerla |
| Cabecera | Muestra el nombre real del dispositivo (Ajustes > Sistema > Información) |
| Saludo | «Buenos días / Buenas tardes / Buenas noches», con tu nombre o «a todos», según la hora del dispositivo |
| Noticias RSS | Hasta 5 feeds propios (Opciones > Noticias); no trae fuentes predefinidas |
| Entradas | Sin entradas que listar, el botón envía la misma tecla que el botón Entrada del mando |
| Recuperación | Inicio 3 veces en 10 s abre un menú para reactivar Google TV Home o restablecer los ajustes |

El diseño, los fondos, el salvapantallas de reloj y el lector de noticias son los del launcher original.

## Instalar

Ver [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md). En resumen, con el paquete de la release
(APK, `instalar.ps1`, `revertir.ps1`, `reparar-inicio.ps1` y `adb`):

```powershell
.\platform-tools\adb.exe connect <IP-del-Chromecast>:5555
powershell -ExecutionPolicy Bypass -File .\instalar.ps1
```

Para deshacerlo: `revertir.ps1`. Más detalles en [docs/RECOVERY.md](docs/RECOVERY.md).

## Compilar

Requisitos: JDK 17 o superior y Android SDK (platform 34, build-tools 34.0.0).

```powershell
cd launcher
.\gradlew.bat assembleRelease
```

Sin `launcher/keystore.properties` y `launcher/release.jks` el APK se firma con la clave de
depuración local. **Esos dos archivos no se suben nunca al repositorio** (están en `.gitignore`).

## Estructura

| Carpeta | Contenido |
|---|---|
| `launcher/` | El launcher (Android, Java) |
| `scripts/` | Herramientas ADB de medición y prueba (`nhgc-*`); ver la cabecera de cada script |
| `tools/` | Debloat reversible genérico heredado de CleanYourTV (`disable`, `restore`, `tv.py`) |
| `examples/` | Listas de debloat de otros televisores, como ejemplo del formato |
| `tests/` | Tests de los scripts y herramientas (Python y bash) |
| `docs/` | Documentación |

## Documentación

- [Primeros pasos](docs/GETTING-STARTED.md)
- [Recuperación](docs/RECOVERY.md)
- [Problemas conocidos](docs/KNOWN-ISSUES.md)
- [Personalización](docs/PERSONALIZATION.md) y [presets de experiencia](docs/EXPERIENCE-PRESETS.md)
- [Tarjetas de entradas](docs/INPUT-CARDS.md)
- [Traducciones](docs/LOCALIZATION.md)
- [Procedencia del código](docs/PROVENANCE.md) y [proyectos relacionados](docs/RELATED-PROJECTS.md)
- [Cómo contribuir](CONTRIBUTING.md)

## Licencia y créditos

MIT. Ver [LICENSE](LICENSE), [NOTICE.md](NOTICE.md) y [launcher/NOTICE.md](launcher/NOTICE.md).
Proyecto no afiliado a Google. Las marcas pertenecen a sus propietarios.
