# local-chat
This is a simple console application that allows to create and join chats on local network using sockets.

### Running
To start application you can simply run `./gradlew run`. However, for the better experience it is recommended to open a separate terminal and redirect the output to it by running
```bash
 ./gradlew run --console=plain > /dev/ttys011
```
(replace destination with the result of `tty` in your separate terminal)

After starting the application, follow the prompts. You can select one of two mods:
- `start`: Start server application that will serve as a host for the chat. The port will be chosen automatically and shown to you. Server supports multiple active connections and broadcasts all chat messages to all chat members. Newly connected clients get last 100 chat messages. The server can be stopped by sending EOF to the application's stdin.
- `join`: Start client application that can connect to existing chat host. You will be prompted for port number to connect to. The client can be connected only to one server. If the server closes the connection, you will be prompted to exit the application. To end the connection from the client side, you can send EOF to the application's stdin. 

### Demo
[![asciicast](https://asciinema.org/a/FpRGrqRIC4v3cwEYJcfcbWFFZ.svg)](https://asciinema.org/a/FpRGrqRIC4v3cwEYJcfcbWFFZ)

### Limitations
- If server closes the connection, client can't exit until user sends input
- If client is disconnected, application should be launched again to make another connection.
- No service messages, so only server gets connection and disconnection notifications

### Extra
- Logfile location can be passed via `localChat.logfile` gradle property: 
```bash
./gradlew run --console=plain -PlocalChat.logfile='./logs/client.log' > /dev/ttys011
```
- Actually, there are two server implementations: the default one using `SharedFlow` as main message queue, and experimental one using channels and select expression. You can tell application to use the latter by setting the `localChat.server` property to `channel`. Notice however, that this version does not support chat history for new clients and may experience freezes (some events might not be fully processed until arrival of later events; I'm not sure if it is a `select` issue or my coroutine setup) 

