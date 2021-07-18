import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class Server {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    public Server() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.bind(new InetSocketAddress(8189));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (serverSocketChannel.isOpen()) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key);
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Server();
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel channel = serverSocketChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }

    private void handleRead(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        SocketChannel channel = (SocketChannel) key.channel();
        int read;
        StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            read = channel.read(buffer);
            buffer.flip();

            if (read == -1) {
                channel.close();
                break;
            }

            if (read > 0) {
                while (buffer.hasRemaining()) {
                    stringBuilder.append((char) buffer.get());
                }
                buffer.clear();

            } else {
                break;
            }

            String command = stringBuilder.toString().replaceAll("[\r\n]+$", "");
            if (command.equals("ls")) {
                Path root = Paths.get("./");
                List<Path> paths = Files.walk(root)
                        .collect(Collectors.toList());
                channel.write(ByteBuffer.wrap(paths.toString().getBytes(StandardCharsets.UTF_8)));

            } else if (command.startsWith("cat ")) {
                if (command.split(" ").length > 1) {
                    String fileName = command.split(" ")[1];
                    try {
                        RandomAccessFile randomAccessFile = new RandomAccessFile(fileName, "r");
                        FileChannel fileChannel = randomAccessFile.getChannel();
                        buffer.clear();
                        while (true) {
                            read = fileChannel.read(buffer);
                            if (read == -1) {
                                break;
                            }
                            buffer.flip();
                            channel.write(buffer);
                            buffer.flip();
                        }
                    } catch (IOException e) {
                        channel.write(ByteBuffer.wrap("File not found".getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }
    }
}
