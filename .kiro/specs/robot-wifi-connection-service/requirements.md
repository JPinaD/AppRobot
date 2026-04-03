# Requirements: Robot WiFi Connection Service

## Contexto

AppRobot corre en un tablet Android montado en el robot. Se comunica con AppTerapeuta
a través de MQTT sobre una red WiFi local (hotspot o router). Este servicio gestiona
la verificación de conectividad WiFi y el establecimiento de la conexión MQTT antes
de que la sesión terapéutica pueda comenzar.

## Alcance

Este spec cubre exclusivamente:
- Verificación de conectividad WiFi en el dispositivo Android
- Configuración del broker MQTT (host, puerto, robotId)
- Establecimiento y reconexión de la sesión MQTT
- Exposición del estado de conexión al resto de la app

No cubre: configuración de red WiFi del sistema Android (eso requiere permisos de sistema
o MDM), ni la lógica de sesión terapéutica posterior a la conexión.

---

## Requisitos funcionales

### RF-01: Verificación de conectividad WiFi antes de conectar al broker

**Como** tablet del robot,
**quiero** verificar que hay red WiFi activa antes de intentar conectar al broker MQTT,
**para** mostrar un error claro en lugar de un timeout silencioso.

**Criterios de aceptación:**
- Si no hay WiFi activa, se muestra estado `NO_WIFI` sin intentar conexión MQTT.
- Si hay WiFi pero el broker no responde, se muestra estado `BROKER_UNREACHABLE`.
- La verificación se realiza en background (no en el hilo principal).

---

### RF-02: Configuración persistente del broker y del robotId

**Como** técnico que despliega el robot,
**quiero** poder configurar la IP del broker y el robotId desde la app (no en el código fuente),
**para** poder cambiar de red o de robot sin recompilar.

**Criterios de aceptación:**
- La IP del broker y el robotId se leen de `SharedPreferences` en cada inicio.
- Si no hay configuración guardada, se usan valores por defecto definidos en una constante (no hardcodeados en lógica de negocio).
- Existe una pantalla o mecanismo de configuración accesible desde `RobotSplashActivity`.

**Nota de conflicto resuelto:** `MqttConfig.BROKER_IP = "10.38.229.66"` debe dejar de ser
la fuente de verdad en runtime. Puede mantenerse como valor por defecto de fallback.

---

### RF-03: Conexión MQTT con reconexión automática

**Como** app del robot,
**quiero** que la conexión MQTT se restablezca automáticamente si se pierde,
**para** que una caída momentánea de red no requiera reiniciar la app.

**Criterios de aceptación:**
- Si la conexión MQTT se pierde, se reintenta con backoff exponencial (máx. 3 intentos, intervalos: 2s, 4s, 8s).
- Tras 3 intentos fallidos, el estado pasa a `DISCONNECTED` y se notifica a la UI.
- Mientras se reintenta, el estado es `RECONNECTING`.
- La reconexión no bloquea el hilo principal.

---

### RF-04: Ciclo de vida del cliente MQTT ligado al ciclo de vida de la app

**Como** app del robot,
**quiero** que la conexión MQTT se gestione correctamente cuando la app pasa a background o se destruye,
**para** evitar conexiones huérfanas o estados inconsistentes.

**Criterios de aceptación:**
- La conexión MQTT se desconecta limpiamente cuando la app se destruye (`onDestroy` del componente raíz).
- Si la app vuelve a primer plano y la conexión está caída, se reintenta la conexión.
- El `MqttManager` Singleton no recrea el cliente si ya existe uno conectado.

---

### RF-05: Exposición del estado de conexión como flujo observable

**Como** pantalla de espera (`WaitingSessionActivity`),
**quiero** observar el estado de conexión MQTT en tiempo real,
**para** mostrar feedback visual al usuario (conectando, conectado, error).

**Criterios de aceptación:**
- El estado de conexión se expone como `LiveData<ConnectionState>` desde un `ViewModel`.
- Los estados posibles son: `IDLE`, `NO_WIFI`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `DISCONNECTED`, `BROKER_UNREACHABLE`.
- La UI no llama directamente a `MqttManager`; solo observa el `ViewModel`.

---

### RF-06: Identificador único del robot

**Como** sistema MQTT,
**quiero** que cada robot tenga un `robotId` único y estable,
**para** que los topics MQTT (`robots/{robotId}/command`, etc.) sean correctos.

**Criterios de aceptación:**
- El `robotId` se lee de `SharedPreferences`.
- Si no existe, se genera uno basado en `Settings.Secure.ANDROID_ID` (no UUID aleatorio, para que sea estable entre reinicios).
- El `robotId` se pasa a `RobotMqttClient` en su construcción, no se obtiene de una constante global.

---

## Requisitos no funcionales

### RNF-01: Sin operaciones de red en el hilo principal
Toda conexión, suscripción y publicación MQTT debe ejecutarse fuera del hilo principal
(el cliente HiveMQ async ya lo garantiza, pero los callbacks deben dispatchar a Main
solo para actualizar UI).

### RNF-02: Sin dependencias de red externas
El broker MQTT es local (LAN/hotspot). No se requiere internet. El servicio no debe
asumir conectividad a internet.

### RNF-03: Compatibilidad Android API 26+
`minSdk = 26`. Las APIs de red usadas deben ser compatibles desde API 26.
`ConnectivityManager.NetworkCallback` es la API correcta (no `WifiManager.isWifiEnabled()`
que está deprecated en API 29+).

---

## Conflictos arquitectónicos pendientes de decisión

Estos conflictos deben resolverse antes de implementar:

| # | Conflicto | Decisión requerida |
|---|-----------|-------------------|
| C1 | Existen `data/model/RobotCommand.java` y `mqtt/message/RobotCommand.java` — dos clases con el mismo nombre | Eliminar `data/model/RobotCommand.java` y usar solo `mqtt/message/RobotCommand.java`, o renombrar una |
| C2 | `HardwareBluetoothClient` está vacío pero hay permisos Bluetooth en el Manifest | Decidir si Bluetooth es parte del sistema actual o se elimina del scope |
| C3 | `MqttManager` Singleton se construye en el constructor (no lazy), sin contexto Android | Refactorizar para recibir contexto o usar `Application` como scope |
| C4 | `WaitingSessionActivity` recibe `profile_id` por Intent pero no lo usa para nada | Definir qué hace la app con el perfil seleccionado mientras espera la sesión |
