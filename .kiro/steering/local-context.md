# AppRobot - Contexto Específico

## Tecnología
- Android (Java), Gradle, compileSdk 36, minSdk 26.
- Room para persistencia local.
- HiveMQ MQTT client (implementado, no activo en flujo principal).
- Gson para serialización.
- AndroidX ViewModel/LiveData.

## Arquitectura
- MVVM con ViewModels y LiveData.
- TCP server en puerto 9000 (recibe conexiones de AppTerapeuta).
- NSD para anunciarse en red local.
- Bluetooth SPP para comunicarse con Arduino/HC-05.
- RobotNetworkService (bound service) mantiene red activa.
- WaitingSessionActivity es el hub central que enruta mensajes.
- SessionNetworkHolder (static) da acceso a TcpServer y BluetoothRobotManager desde ViewModels.
- Modo kiosko activo para impedir que alumnos salgan de la app.

## Actividades educativas implementadas
1. **Pictogramas** (activity_pictogram): selección de pictograma en grid.
2. **Reconocimiento Emocional** (activity_emotion): identificar emoción correcta.
3. **Escenarios Sociales** (activity_social): elegir respuesta social adecuada.
4. **Secuencias Visuales** (activity_sequence): memorizar y reproducir orden.
5. **Momento Calma** (activity_calm): ejercicio de respiración pasivo.
6. **Turnos Sociales** (activity_turns): turnos entre robots.

## Reglas UI/UX (contexto TEA)
- Interfaces simples, bajo ruido visual.
- Colores suaves y agradables.
- No ruidos fuertes ni estímulos excesivos.
- Feedback inmediato y predecible.
- Jerarquía visual clara, textos directos.
- No proponer interfaces recargadas ni elementos decorativos sin función.

## Stubs pendientes de implementar
- hardware/ package (RobotStateMapper, SensorEventInterpreter, RobotCommandDispatcher)
- bluetooth/ (RobotMessageParser, RobotMessageBuilder, HardwareBluetoothClient, ConnectionHeartbeatManager)
- Algunos ViewModels (WaitingSessionViewModel, RobotStateViewModel, ActivityPlayerViewModel)
