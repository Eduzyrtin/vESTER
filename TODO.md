# TODO vESTER (MultiWallet)

La idea: una app sencilla para llevar tus cuentas a mano y lo que compartes con otra gente, sin conectar bancos.
Todo funciona sin internet y sin registro.

Tamaño: 🟢 pequeño (una tarde) · 🟡 medio (unos días) · 🔴 grande. 🗄️ = cambia la base de datos (hay que subir a la v5 con migración y test).

## Por dónde empezaría

Lo que más se nota para el esfuerzo que cuesta:

1. Categorías en los movimientos, con su gráfica en Informes.
2. Personas en las deudas, con el saldo neto de cada una.
3. ~~Resumen del mes y saldo total arriba en la pantalla principal.~~ Hecho.
4. ~~Deshacer al borrar (Snackbar)~~ hecho en movimientos; falta en deudas y cuentas.
5. ~~Formularios con campos Material y Pago/Ingreso como botones segmentados.~~ Hecho.
6. Presupuesto mensual y metas de ahorro.
7. Bloqueo con huella y modo discreto.
8. Widget de pantalla de inicio.
9. Exportar a CSV.
10. ~~Pasar a Material 3 manteniendo los colores.~~ Hecho.

## Funciones nuevas

### Organizar los movimientos

- [x] 🟡🗄️ **Categorías** (comida, transporte, ocio, casa...): icono y color, con unas cuantas de serie y la opción de crear las tuyas. Se eligen al apuntar el movimiento.
  - Hecho: lista de categorías con el gasto del mes y barra de presupuesto, selector en el formulario, categoría en cada fila y avisos al pasar del 80 % y del 100 %. Pendiente: tarta en Informes y filtro por categoría.
  - Informes con una tarta por categoría y filtro por categoría en las listas.
- [x] 🟢 **Autocompletar el concepto** con los ya usados. Si eliges uno, propone el mismo importe y la misma categoría de la última vez.
- [x] 🟡🗄️ **Plantillas o favoritos**: movimientos frecuentes ("Café 1,50", "Bono bus 20") que se apuntan con un toque, desde la app o desde el widget.
- [x] 🟡 **Buscar y filtrar** movimientos por texto, importe (más de, menos de), rango de fechas, cuenta y tipo (pago, ingreso, transferencia).
- [x] 🟢🗄️ **Notas** en un movimiento: un texto largo aparte del concepto.
- [x] 🟡🗄️ **Foto del ticket** adjunta al movimiento: cámara o galería, guardada en el almacenamiento privado de la app y dentro de la copia de seguridad. (Hecho con cámara y galería; la foto vive en la carpeta privada de la app y NO va en la copia de seguridad de la base de datos.)
- [ ] 🟢 **Duplicar un movimiento** desde su menú de opciones.
- [ ] 🟡 **Selección múltiple** en las listas para borrar, mover a otra cuenta o cambiar la categoría de varios a la vez.

### Cuentas

- [x] 🟢🗄️ **Archivar una cuenta**: se oculta de la pantalla principal sin borrar su historial (cuentas cerradas o huchas viejas).
- [ ] 🟢🗄️ **No sumar una cuenta al total**, por ejemplo la de un amigo que llevas tú.
- [x] 🟢🗄️ **Icono o emoji por cuenta**: 🏦 💳 🐷 💵 ✈️...
- [ ] 🟡🗄️ **Grupos de cuentas** (día a día, ahorro, compartidas), plegables y con su subtotal.
- [ ] 🟡🗄️ **Límite o saldo mínimo** por cuenta, con aviso si baja de ahí.
- [ ] 🟡🗄️ **Cuenta de tarjeta de crédito**: el gasto se acumula y un día al mes se "liquida" contra la cuenta del banco con una transferencia programada.

### Compartir gastos con otra gente

Es lo que diferencia la app: llevar las cuentas con amigos, la pareja o los compañeros de piso.

- [x] 🟡🗄️ **Personas** como algo propio, no solo dentro del concepto de la deuda ("Cena · Ana"). (Hecho como campo de la deuda; falta una lista de personas con su saldo.)
  - Ficha de cada persona: lo que te debe y lo que le debes, con el saldo neto ("Ana te debe 23,50 €").
  - Las deudas se agrupan por persona y al hacer un gasto compartido se elige de la lista.
- [x] 🟡🗄️ **Pagos parciales de una deuda** ("me ha devuelto 10 de los 30"), con historial y una barra de progreso. (Hecho el pago parcial con barra de progreso; falta el historial.)
- [x] 🟢🗄️ **Fecha límite en una deuda**, con aviso el día antes y la deuda vencida en rojo. (Hecho el rojo y la fecha; falta el aviso el día antes.)
- [ ] 🟢 **Compartir una deuda o un resumen**: un mensaje listo para WhatsApp o Telegram ("Me debes 23,50 € de la cena del 12/10") o una imagen bonita de la tarjeta.
- [x] 🔴🗄️ **Grupos tipo Splitwise ligero** para un viaje, el piso o una cena: (Hecho: grupos, miembros, gastos a partes iguales, saldos, pagos sugeridos que se apuntan con un toque y deshacer; falta el reparto desigual y crear deudas al cerrar el grupo.)
  - Lista de participantes y gastos de cada uno.
  - Balance de quién debe a quién y "simplificar deudas" para que haya el mínimo de pagos.
  - Al cerrar el grupo se crean las deudas.
  - Todo local: sin cuentas de usuario ni servidor.
- [x] 🟡 **Reparto por porcentajes o por partes** en el gasto compartido (60/40, o "Ana 2 partes, yo 1"), no solo a partes iguales o a mano. (Hecho por partes; falta el reparto por porcentajes.)
- [ ] 🟡🗄️ **Cuenta conjunta** con un porcentaje de cada uno: en Estadísticas e Informes se cuenta solo "tu parte".
- [ ] 🔴 **Intercambiar un grupo con un amigo** sin servidor: exportar e importar un archivo `.vester` o un código QR con los gastos del grupo, y fusionar lo nuevo.

### Ahorro y presupuestos

- [x] 🟡🗄️ **Presupuesto mensual**, total o por categoría: barra de progreso, color según lo gastado (verde, naranja, rojo) y aviso al pasar del 80 % y del 100 %.
- [x] 🟡🗄️ **Metas de ahorro** ("Viaje a Japón: 1.200 € para junio"): progreso, cuánto apartar al mes para llegar y una pequeña celebración al conseguirla. (Hecho: progreso, ritmo y cuánto apartar al mes; falta la celebración.)
  - Puede ir ligada a una cuenta tipo hucha.
- [ ] 🟢 **Previsión de fin de mes**: saldo actual más las programadas que faltan, o "a este ritmo terminarás el mes con X".
- [ ] 🟡 **Retos de ahorro**: 52 semanas, redondeo de cada pago a la hucha, "no gastar" un día...
- [x] 🟢 **El mes empieza el día que cobras**: elegir el día de inicio (por ejemplo el 25) para Informes, presupuestos y el resumen.

### Apuntar más rápido

- [x] 🟡 **Widget de pantalla de inicio** (Jetpack Glance): saldo total o de una cuenta, botón "+" y los favoritos. (Hecho con RemoteViews: saldo total y botón +; falta el de una cuenta y los favoritos.)
- [x] 🟢 **Botón en los ajustes rápidos** (Quick Settings tile) para apuntar un gasto desde cualquier sitio.
- [x] 🟢 **Accesos directos dinámicos** con las cuentas más usadas: ahora son fijos (movimiento, deuda, transferencia).
- [ ] 🟢 **Calculadora en el campo de importe**: escribir `12,40+3,5` o `90/3` y que lo calcule.
- [ ] 🟡 **Recibir texto compartido**: compartir con vESTER un SMS, un correo o un texto del banco y que lo convierta en sugerencia con el mismo `NotificationParser`.
- [ ] 🟡 **Escanear un ticket** con ML Kit (reconocimiento de texto en el propio móvil, sin internet) para sacar el total y la fecha.
- [ ] 🟡 **Wear OS**: una tile con el saldo y el apuntado rápido desde el reloj.

### Análisis

- [ ] 🟢 **Resumen del mes en la pantalla principal**: ingresos, gastos y diferencia del mes actual, con la comparación con el anterior (+12 %).
- [ ] 🟡 **Evolución del saldo** en el tiempo, como gráfica de línea por cuenta y del total.
- [ ] 🟢 **Patrimonio neto**: el saldo de todas las cuentas, más lo que te deben, menos lo que debes.
- [ ] 🟢 **Más datos en Informes**: media diaria, día que más se gastó, los 5 mayores gastos y las categorías que más suben.
- [ ] 🟢 **Mapa de calor en el Calendario**: los días con más gasto, más oscuros.
- [ ] 🟡 **Resumen anual** estilo "Wrapped": unas pantallas con lo más destacado del año para ver y compartir.

### Varias monedas

- [ ] 🟡 **Monedas con su código ISO** (EUR, USD, MXN, ARS...) en vez de los 7 símbolos actuales, con el formato de cada una.
- [x] 🟡🗄️ **Moneda principal y cambio manual por cuenta**, para sumar bien en Combinación, Estadísticas e Informes. Ahora, si las monedas son distintas, el total sale sin símbolo. (Hecho: moneda principal y cambio manual por moneda, usado en el saldo total, Combinación, Estadísticas y el widget.)
  - Opcional: traer el cambio del BCE una vez al día; si no hay internet, se usa el último guardado.
- [ ] 🟡 **Transferencia entre monedas**: indicar lo que sale y lo que llega.

### Datos, seguridad y avisos

- [x] 🟡 **Bloqueo con huella o PIN** al abrir (BiometricPrompt) y ocultar la app en la vista de recientes (FLAG_SECURE).
- [x] 🟢 **Modo discreto**: los saldos salen como `••••` hasta que los tocas, para enseñar la app en público.
- [x] 🟢 **Exportar a CSV** todo, o una cuenta o un rango de fechas, para abrirlo en Excel o Sheets.
- [x] 🟡 **Importar CSV** de otra app o del extracto del banco, eligiendo qué columna es cada cosa.
- [x] 🟡 **Informe en PDF** de un mes o de una deuda, para enviarlo. (Hecho el informe de un mes; falta el de una deuda.)
- [x] 🟡 **Copia automática** cada semana a una carpeta que elijas (Drive incluido, con el selector de archivos) y aviso si hace mucho de la última. (Hecho con la carpeta que elijas y las últimas 4 copias; para restaurar usa "Restaurar copia de seguridad" del menú.)
- [x] 🟢 **Deshacer al borrar** en deudas y cuentas (en los movimientos ya funciona al deslizar): Snackbar con "Deshacer" en vez del diálogo de confirmación.
- [ ] 🟢 **Recordatorio diario** opcional ("¿Has apuntado los gastos de hoy?") a la hora que elijas.
- [x] 🟢 **Avisos de deudas vencidas, presupuesto superado y programadas apuntadas**: todos en el mismo canal de notificaciones y con un solo permiso. (Hecho: presupuesto, programadas y deudas que vencen mañana o ya vencidas.)

### Ajustes e idiomas

- [x] 🟡 **Pantalla de Ajustes propia**: moneda principal, día de inicio del mes, primer día de la semana, tema (sistema, claro u oscuro), ruedas para importes, bloqueo, recordatorios, copias y notificaciones. Ahora están repartidos en el menú.
- [x] 🟢 **Idioma por app** de Android 13 (`localeConfig`): hecho.
- [x] 🟡 **Más idiomas**: español, inglés, francés, italiano, portugués, catalán, gallego y euskera.
- [ ] 🟢 **Página "Acerca de"**: el menú existe pero está oculto. Versión, licencias de las librerías y enlace a valorar en Play.

## Mejoras gráficas

Casi todo está hecho: ver "Hecho en la 2.0 (diseño)" al final. Esto es lo que queda.

- [ ] 🟡 **Fuente propia para los importes** (Rubik, Manrope o Nunito). Ya hay escala tipográfica (`TextAppearance.App.*`), peso medio y cifras de ancho fijo, pero falta el archivo de la fuente (hay que bajarlo de Google Fonts, licencia OFL) o usar Downloadable Fonts.
- [x] 🟡 **Icono de categoría en cada movimiento** (cuando haya categorías). El icono de las cuentas ya está hecho.
- [ ] 🟢 **Gesto de duplicar** un movimiento deslizando (ahora está en el menú de pulsación larga).
- [ ] 🟡 **Transiciones del resto de pantallas** (ahora solo la tarjeta de la cuenta se "abre" hasta el detalle; el resto usa la animación por defecto).
- [ ] 🟡 **Gráficas**: se valoró pasar de MPAndroidChart (sin mantenimiento desde 2020) a Vico y se deja como está, ya restilizada; Vico pide reescribir las cuatro gráficas y sus animaciones. Revisar si aparece un fallo con una versión nueva de Android.
- [ ] 🟡 **Accesibilidad con TalkBack**: todos los iconos tienen descripción y los botones táctiles miden 48 dp, pero falta recorrer la app con TalkBack en un móvil real.
- [x] 🟢 **Animar las salidas y los cambios de orden** en las listas (animador común con tiempos más pausados).

### Colores, textos y gestos (hecho)

- [x] Paleta cálida nueva (fondo crema, barras y contenedores con tinte, naranja de acento más profundo y oscuro cálido) con los estados `positive` / `negative` unificados.
- [x] Tarjetas de cuenta: de noche se oscurecen más (62 % hacia el fondo) y siempre llevan texto claro legible.
- [x] Textos: el secundario ahora se distingue del principal (`textColorSecondary` apuntaba al principal) y los layouts usan `textColorPrimary/Secondary` del tema en vez de colores fijos.
- [x] Gestos: en las tarjetas de cuenta deslizar a la derecha edita, a la izquierda borra (con confirmación), pulsación larga reordena y un botón ⋮ ofrece Editar / Borrar. Programadas y sugerencias usan los mismos gestos que los movimientos y las deudas.
- [x] 🟢 Los 30 colores del selector se han comprobado (contraste AA de día y de noche, con un test) y "Archivar" ya existe.

### Código

- [x] 🟢 **Avisos de lint**: quedan 6 (los iconos del lanzador antiguos para Android 7 y el aviso de targetSdk).
- [x] 🟢 **Formulario de transferencia** con el mismo orden y espaciado que el de movimiento (importe, cuentas, fecha).

## Pendiente de la 2.0

- [ ] Probar la lectura de notificaciones con notificaciones reales de Google Wallet y de tu banco: es lo único que no se puede comprobar sin tu móvil (los formatos están probados con tests, pero cada app escribe a su manera, y los nombres de paquete de las apps nuevas hay que confirmarlos).
- [ ] Reconocer monedas con la cámara (Coin-Counter): descartado por ahora; se hizo el contador manual.

## Hecho en la 2.0

- [x] Sumas: importes con `BigDecimal` y en céntimos, operaciones atómicas en la base de datos y tests que lo comprueban.
- [x] Permisos para el backup: no hacen falta. La copia se guarda y se restaura con el selector de archivos del sistema.
- [x] Programar transacciones: con una fecha futura en el formulario, se apuntan solas ese día (WorkManager + al abrir la app). Lista en "más → Programadas".
- [x] Transacciones recurrentes: cada día, semana, mes o año. Si el móvil estuvo apagado se ponen al día, y los meses conservan el día (un pago del 31 vuelve al 31).
- [x] Transacciones conjuntas: "más → Gasto compartido" (o desde el menú de una transacción). Se puede pagar desde varias cuentas y repartir con otras personas, que quedan como deudas a cobrar.
- [x] Tablet: dos columnas de cuentas y tres de deudas.
- [x] Limitar 30 transacciones: las listas cargan de 30 en 30 al bajar.
- [x] La combinación de cuentas en negativo como anillo interior, con su suma en el centro.
- [x] Gastos por mes/año: "más → Informes", con gráfica y filtro por cuenta. Las transferencias no cuentan como gasto.
- [x] Página de estadísticas: número de cuentas, transacciones, deudas, programadas y sugerencias con sus ids, saldo total y versiones.
- [x] El "transformation" en AccountTransaction: la cuenta y sus movimientos se observan por id con `switchMap`.
- [x] Números como en iOS: los saldos y totales ruedan al cambiar, y hay una opción (menú → "Ruedas para importes") para escribir los importes con ruedas de cifras.
- [x] Lectura de notificaciones de Google Pay/Wallet, bancos y correo (Gmail, Outlook): quedan como sugerencias para confirmar en "más → Sugerencias". Leer el correo con la API de Gmail se descartó: exige una auditoría de seguridad de pago.
- [x] Coin-Counter: "más → Contar efectivo", con monedas y billetes de €, $ y £; apunta el total como ingreso o cuadra el saldo de la cuenta.
- [x] Comentar todo: clases y funciones documentadas.
- [x] Tema oscuro: sigue al ajuste del sistema.
- [x] Página con calendario en "más": al elegir un día salen sus transacciones y sus deudas.
- [x] Calendario para las transferencias y transacciones: todos los formularios tienen selector de fecha.
- [x] Más botones de dividir (÷2, ÷3, ÷4 y ÷N) con estilo redondeado, en crear y modificar deuda.
- [x] Borrar una transferencia entera de una vez (también los gastos pagados desde varias cuentas).

## Hecho en la 2.0 (diseño)

- [x] **Material 3** manteniendo la paleta (gris claro, naranja de acento, verde y rojo): botones, interruptores, diálogos y menús coherentes. Los colores de los controles son el naranja y las barras siguen en gris (`barColor`).
- [x] **Pantalla principal**: cabecera con el saldo total (con el efecto de rodillo) y el resumen del mes (ingresos y gastos), tarjetas de cuenta más redondeadas con degradado suave, sin textos que se desplazan solos y atenuadas por la noche.
- [x] **Barra de abajo** con cuatro accesos con etiqueta (Combinación, Movimientos, Deudas, Más), botón "Nuevo" con una hoja inferior (movimiento, transferencia, deuda, gasto compartido) y hoja "Más" con iconos de colores y la insignia de sugerencias.
- [x] **Listas de movimientos agrupadas por día** ("Hoy", "Ayer", "lun, 12 oct") con el total de cada día, icono de color (ingreso, pago o transferencia) y el importe a la derecha; las mismas filas en Todos, Cuenta, Calendario y Programadas.
- [x] **Gestos**: deslizar a la izquierda borra con "Deshacer" (devuelve el saldo y lo restaura igual si se deshace), a la derecha edita; los movimientos de transferencias y gastos repartidos abren sus opciones.
- [x] **Deudas**: resumen "te deben / debes", tarjeta con avatar con la inicial, importe y botones de 48 dp, barra para saldar las marcadas, pantalla vacía y una columna en móvil.
- [x] **Formularios**: campos Material contorneados con el error bajo el campo, Pago/Ingreso (y a pagar/a cobrar, positivo/negativo) como botones segmentados, desplegables y fecha con aspecto de campo, botón "Guardar" con texto.
- [x] **Selector de color de la cuenta**: paleta de 30 colores en cuadrícula con una vista previa de la tarjeta que cambia con el nombre y el color. Se quitaron los ~140 colores con nombre; las cuentas antiguas conservan su color.
- [x] **Detalle de cuenta** con cabecera redondeada, nombre, saldo grande y moneda.
- [x] **Gráficas** con barras redondeadas, verde para ingresos y rojo para pagos, rejilla discontinua suave y colores del tema; el mismo estilo en Informes.
- [x] **Cifras de ancho fijo** (`tnum`) en saldos, importes y totales.
- [x] **Modo oscuro** revisado: tarjetas atenuadas, colores de ingreso y pago más claros y fondo de las hojas.
- [x] **Icono temático** (monocromo) de Android 13, **gesto de volver predictivo** de Android 14+, fondo de la pantalla de inicio del color del tema y vibración suave al deslizar.
- [x] **Imágenes de la presentación inicial** rehechas con capturas de la interfaz nueva.
- [x] **Limpieza**: quitados 122 colores, 22 dibujos y 32 textos sin usar; lint pasó de 135 a unos 48 avisos y 0 errores.

## Hecho en la 2.0 (segunda pasada de diseño y pendientes)

- [x] **Formularios unificados**: movimiento (nuevo y modificar), deuda (crear y modificar) y cuenta (añadir y editar) comparten layout y, en cuentas, también el código (`AccountFormActivity`). Ids con nombre en vez de `textView16` o `editText2`.
- [x] **Desplegables de Material** (`DropdownField`) en lugar de `Spinner`, **selector de fecha de Material** y **chips** para elegir cada cuánto se repite un movimiento.
- [x] **Icono (emoji) por cuenta**: 20 emojis para elegir en el formulario, se ven en la tarjeta, en el detalle y en la vista previa. Base de datos v5 con migración automática 4→5 y su prueba.
- [x] **Cabecera de la principal que se pliega** al bajar por la lista (el saldo y el resumen del mes).
- [x] **Tablet con dos paneles**: cuentas a la izquierda y el detalle de la elegida a la derecha (`AccountDetail`, el mismo detalle que en móvil).
- [x] **Transición de la tarjeta al detalle** de la cuenta (la tarjeta se "abre" hasta ocupar la pantalla).
- [x] **Pantallas vacías** con icono y pista en Movimientos, Cuenta, Calendario, Programadas, Sugerencias y Deudas completadas.
- [x] **Gestos**: deslizar una deuda a la derecha la salda y a la izquierda la borra; "Duplicar" en el menú de un movimiento; las listas entran animadas.
- [x] **Teclado numérico propio** para los importes (menú → "Teclado numérico para importes"), junto a las ruedas, que son excluyentes.
- [x] **Colores del sistema (Material You)** como opción del menú, en móviles que los tengan.
- [x] **Pantalla de inicio propia** (librería oficial de splash) con el logo y una salida suave.
- [x] **Tutorial propio** (foco sobre cada elemento) que sustituye a MaterialShowcaseView, que estaba abandonada; ya no se incluye esa dependencia.
- [x] **Gasto compartido, Contar efectivo y Estadísticas** con el estilo nuevo (campos Material, tarjetas con borde, botones tonales).
- [x] **Letra grande**: probada al 200 %. El saldo de la tarjeta baja de línea si no cabe, los chips del resumen se envuelven, las hojas pasan a dos columnas y las etiquetas de la barra inferior se ajustan.
- [x] **Accesibilidad**: los contadores de cifras (que se dibujan a mano) se leen con TalkBack, y las casillas y botones de monedas tienen descripción.
- [x] **Movimientos programados**: tocar uno lo edita (cuenta, importe, concepto, repetición y próxima fecha, que solo puede ser futura); si no cambian la fecha ni la repetición, la serie sigue con sus repeticiones ya apuntadas.
- [x] **Aviso al apuntarse un programado** cuando lo hace la tarea en segundo plano (permiso de notificaciones pedido la primera vez que se programa algo).
- [x] **Más apps en las sugerencias de notificaciones**: bunq, Monzo, Starling, Trade Republic, Ibercaja, Cash App, Venmo, Yahoo Mail y Proton Mail.
- [x] **Lint**: de 135 avisos a 20 y 0 errores; quitados los recursos sin usar y el aviso que detectó un `Flow` que apuntaba a un id renombrado.
