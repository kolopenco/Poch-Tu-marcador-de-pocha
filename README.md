<p align="center">
  <img src="web/icons/poch-256.png" width="128" alt="Icono de Poch!">
</p>

<h1 align="center">Poch! · Tu marcador de pocha</h1>

<p align="center">
  Marcador, historial y estadísticas para partidas de <b>La Pocha</b> con baraja española.<br>
  Gratis, sin anuncios, sin registro y sin Internet.
</p>

<p align="center">
  <a href="https://kolopenco.github.io/Poch-Tu-marcador-de-pocha/"><img src="https://img.shields.io/badge/iPhone_y_navegador-Abrir_la_app_web-1E7BF2?style=for-the-badge&logo=safari&logoColor=white" alt="Abrir la app web"></a>
  &nbsp;
  <a href="https://github.com/kolopenco/Poch-Tu-marcador-de-pocha/releases/latest/download/Poch.apk"><img src="https://img.shields.io/badge/Android-Descargar_APK-2BC46A?style=for-the-badge&logo=android&logoColor=white" alt="Descargar APK para Android"></a>
</p>

<p align="center">
  <img src="docs/preview.png" alt="Pantallas de Poch!: partida, detalle, estadísticas y ficha de jugador">
</p>

---

## Qué hace

- **Marcador en directo.** Una tarjeta por jugador con su total, la variación de la ronda y los aciertos y fallos. Se apunta cada ronda con un par de toques: en modo **sencillo** (cuántas hizo o por cuántas falló) o **completo** (pedidas y hechas).
- **Las reglas de la Pocha, ya hechas.** Rondas que suben, repiten el máximo y bajan; quién reparte en cada ronda; **subastas** con su adjudicatario; **Oros ×2**; y comprobación de que las bazas cuadran antes de guardar.
- **¿Alguien se equivocó?** Cualquier ronda se puede corregir: los totales se recalculan, queda marcada en amarillo y se puede deshacer.
- **Final de partida.** Clasificación con podio. Si hay empate, la app pregunta en ese momento si compartís la victoria o desempatáis a una carta.
- **Historial y estadísticas.** Victorias, posición media, rondas acertadas, subastas ganadas… por jugador o comparando a varios, y también **solo en las partidas que habéis jugado juntos**.
- **Vuestras reglas.** Perfiles con puntuación, multiplicadores por palo, secuencia de rondas y política de empates.
- **Copias de seguridad compartibles.** Exporta una copia y pásasela a otro móvil: al importarla elige **sustituir** o **mezclar**, uniendo cada jugador con el tuyo para sumar estadísticas. Android y la versión web usan el mismo formato.

## Cómo instalarla

### iPhone (y cualquier navegador)

1. Abre **[kolopenco.github.io/Poch-Tu-marcador-de-pocha](https://kolopenco.github.io/Poch-Tu-marcador-de-pocha/)** en **Safari**.
2. Pulsa **Compartir** (el cuadrado con la flecha) › **Añadir a pantalla de inicio**.
3. Ya tienes el icono de Poch!: se abre a pantalla completa y funciona sin conexión.

Los datos se guardan en tu propio móvil. Haz una copia de seguridad de vez en cuando (Ajustes › Copias de seguridad).

### Android

1. Descarga **[Poch.apk](https://github.com/kolopenco/Poch-Tu-marcador-de-pocha/releases/latest/download/Poch.apk)** desde la [última versión](https://github.com/kolopenco/Poch-Tu-marcador-de-pocha/releases/latest).
2. Ábrelo y permite instalar desde esta fuente si el móvil lo pide.
3. Las versiones nuevas se instalan encima sin perder las partidas.

También puedes usar la versión web en Android desde Chrome (menú › *Instalar aplicación*).

## Pasarse partidas entre amigos

1. Quien tiene las partidas: **Ajustes › Copias de seguridad › Exportar copia** y la envía (WhatsApp, correo, AirDrop…).
2. Quien la recibe: **Importar copia** › **Mezclar**.
3. Para cada jugador de la copia, elige si es uno de los tuyos o uno nuevo. Las partidas se añaden sin duplicarse y las estadísticas se suman.

## Por dentro

| | Android | Web (iPhone) |
|---|---|---|
| Código | Java nativo, interfaz hecha por código, sin librerías | HTML, CSS y JavaScript sin dependencias |
| Datos | En el móvil (SharedPreferences) | En el navegador (localStorage) |
| Sin Internet | Sí | Sí (service worker) |
| Lógica y pruebas | `Engine.java` · 21 pruebas | `engine.js` (misma lógica) · 15 pruebas |

```
android/   Código de la app Android (Gradle)
web/       App web publicada en GitHub Pages
tests/     Pruebas de la lógica de la versión web (node tests/engine.test.js)
docs/      Capturas para esta página
```

- La web se publica sola en GitHub Pages con cada cambio en `web/` (workflow **Web**).
- El APK se compila y se sube a **Releases** con el workflow **Android**, que se lanza a mano. La clave de firma está guardada como secreto del repositorio, no en el código.

## Créditos

Idea y diseño: [@kolopenco](https://github.com/kolopenco). Desarrollo con la ayuda de Claude.
Tipografía [Inter](https://github.com/rsms/inter) (SIL Open Font License 1.1, ver `web/fonts/OFL.txt`).
