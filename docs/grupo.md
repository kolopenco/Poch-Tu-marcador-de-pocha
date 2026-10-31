# Jugar en grupo: guía para crear el vuestro

Un **grupo de juego** comparte jugadores, partidas y estadísticas entre vuestros móviles (Android y iPhone), sin pasaros archivos.

- **Solo una persona** del grupo hace esta guía (unos 10 minutos). Los demás solo necesitan la invitación.
- Los datos se guardan en **vuestro propio proyecto de Firebase** (de Google), gratis. Poch! no tiene servidor: la app pública no lleva ninguna dirección; la configuración viaja solo dentro de vuestras invitaciones.
- No hacen falta cuentas: cada móvil entra de forma anónima con la clave de la invitación.

---

## 1. Crear el proyecto

1. Entra en **[console.firebase.google.com](https://console.firebase.google.com)** con tu cuenta de Google.
2. Pulsa **Crear un proyecto** (o «Añadir proyecto»).
3. Ponle un nombre, por ejemplo `pocha-amigos`, y continúa.
4. Cuando te pregunte por **Google Analytics**, desactívalo (no hace falta) y pulsa **Crear proyecto**.

> El plan gratuito (Spark) sobra para un grupo de amigos y no pide tarjeta.

## 2. Activar el acceso anónimo

1. En el menú de la izquierda: **Seguridad › Authentication** (en versiones anteriores, «Compilación › Authentication»; el icono es una silueta de personas) y pulsa **Comenzar**.
2. En la pestaña **Método de acceso** (Sign-in method), elige **Anónimo**.
3. Actívalo y pulsa **Guardar**.

## 3. Crear la base de datos

1. En el menú: **Compilación › Firestore Database** y pulsa **Crear base de datos**.
2. Si te pregunta la edición, deja la **Standard**.
3. Ubicación: una de Europa, por ejemplo `europe-southwest1 (Madrid)` o `eur3`. *No se puede cambiar después.*
4. Elige **modo de producción** y pulsa **Crear**.

## 4. Pegar las reglas de seguridad

Las reglas hacen que solo entre quien tenga vuestra invitación.

1. En Firestore Database, abre la pestaña **Reglas**.
2. Borra todo lo que haya y pega las reglas. Están en el archivo **[firestore.rules](firestore.rules)** de esta misma carpeta: ábrelo, pulsa el botón de copiar (arriba a la derecha del código) y pégalo.
3. Pulsa **Publicar**.

## 5. Copiar la configuración

1. Pulsa la **rueda ⚙ › Configuración del proyecto**.
2. Baja hasta **Tus apps** y pulsa el icono **web `</>`**.
3. Ponle de nombre `Poch`, **no** marques Firebase Hosting y pulsa **Registrar app**.
4. Verás un bloque como este. **Cópialo entero** (como mínimo, que vayan `apiKey` y `projectId`):

```js
const firebaseConfig = {
  apiKey: "AIza…",
  authDomain: "pocha-amigos.firebaseapp.com",
  projectId: "pocha-amigos",
  storageBucket: "pocha-amigos.firebasestorage.app",
  messagingSenderId: "…",
  appId: "…"
};
```

## 6. Crear el grupo en Poch!

1. En Poch!: **Ajustes › Grupos de juego › Crear un grupo nuevo**.
2. Pulsa **Pegar** (o pega a mano) el bloque del paso 5.
3. Ponle nombre al grupo, di **quién eres tú** y pulsa **Crear grupo**.
4. Pulsa **Compartir** y manda la invitación a tus amigos por WhatsApp o correo.

¡Listo! Desde **Grupos de juego** puedes volver a invitar, ver qué móviles están dentro, cambiar el nombre o crear una invitación nueva.

---

## Para tus amigos

- **iPhone:** abrir el enlace de la invitación en Safari. Poch! se abre y pregunta quién eres.
- **Android:** copiar el mensaje de la invitación y en Poch! ir a **Ajustes › Grupos de juego › Unirse con una invitación** (se pega solo). También se puede *Compartir* el mensaje con Poch!.

Al unirse, cada uno dice **quién es** y, si ya tenía jugadores en su móvil, puede **unirlos** con los del grupo para que se sumen sus partidas y estadísticas.

## Cómo funciona

- **Al abrir la app** se bajan las partidas nuevas del grupo.
- **Al cerrar una partida** (o al corregirla o borrarla después) se sube.
- La partida **en curso no se comparte**: solo se sube cuando se cierra.
- Al empezar una partida se elige el **grupo** con el que se compartirá (o «Sin grupo»).
- Un móvil puede estar en **varios grupos**.

## Cambiar de móvil o reinstalar

El grupo recuerda a tu móvil por una identidad guardada en la app. Si borras la app sin más, la pierdes (y con ella el papel de creador).

- Haz de vez en cuando **Ajustes › Copias de seguridad › Exportar copia**: incluye tus grupos.
- En el móvil nuevo, **Importar copia** y vuelves a estar dentro, como el mismo móvil (y como creador, si lo eras).
- Esa copia funciona como una llave del grupo: guárdala para ti y no se la pases a nadie.
- Para actualizar Poch!, instala la nueva versión **encima**, sin desinstalar.

## Seguridad

- La **invitación es la llave** del grupo: no la publiques. Quien la tenga puede entrar.
- Si alguien que no debe la tiene, en el grupo pulsa **Cambiar la invitación** (la antigua deja de servir) y, si ya entró, **Quitar** su móvil.
- Lo que se guarda en Firebase: nombres y colores de los jugadores y las partidas. Nada más.
- Si algún día quieres borrarlo todo, elimina el proyecto desde la consola de Firebase.

## Si algo falla

| Mensaje | Qué hacer |
|---|---|
| Falta activar el acceso «Anónimo» | Repite el paso 2. |
| No se encuentra la base de datos | Repite el paso 3. |
| Firebase ha denegado el acceso | Revisa que pegaste y **publicaste** las reglas (paso 4). Si te uniste con una invitación, puede que la hayan cambiado: pide una nueva. |
| La configuración no es válida | Vuelve a copiar el bloque del paso 5 entero. |
| No hay conexión | Se sincronizará la próxima vez que abras la app con Internet. |
