
import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Connection implements Commons, Runnable {
    private InetSocketAddress inetAdress = new InetSocketAddress(host, port);
    private SocketChannel channel;
    private Selector selector;
    private boolean login = false, start = false;
    LinkedList<String> listMove = new LinkedList<>();
    LinkedList<Message> messagesList = new LinkedList<>();
    String genShaps = null, playerName, move;
    ClientProcessData clientProcessData;



    public Connection(){
        try {
            this.channel = SocketChannel.open(inetAdress);
            selector = Selector.open();
            channel.configureBlocking(false);
            System.out.println(" Connect to Server...");
            clientProcessData = new ClientProcessData();
            new Thread(clientProcessData).start();
            channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ClientProcessData getClientProcessData(){
        return clientProcessData;
    }

    public void sendMes(byte[] TVL) throws IOException {
        if (channel.isConnected()){
            channel.write(ByteBuffer.wrap(TVL));
            SelectionKey key = channel.keyFor(selector);
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }


    @Override
    public void run() {
        while (true) {
            try {
                selector.select();
                Set<SelectionKey> keySet = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keySet.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey currentKey = keyIterator.next();
                    keyIterator.remove();

                    if (!currentKey.isValid()) {
                        continue;
                    }
                    if (currentKey.isConnectable()) {
                        Connectable(currentKey);
                    }

                    if (currentKey.isReadable()) {
                        readable(currentKey);
                    }

                    if (currentKey.isWritable()) {
                        write(currentKey);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }
    }

    public void Connectable(SelectionKey currentKey) throws IOException {
        SocketChannel channel = (SocketChannel) currentKey.channel();
        if(channel.isConnectionPending()) {
            channel.finishConnect();
        }
        channel.configureBlocking(false);
        channel.register(selector,SelectionKey.OP_READ|SelectionKey.OP_WRITE);
    }

    public void readable(SelectionKey currentKey) throws IOException{
        SocketChannel socketChannel = (SocketChannel) currentKey.channel();
        ByteBuffer bff = ByteBuffer.allocate(1024);

        socketChannel.read(bff);
        addMessage(new Message(currentKey, bff.array()));

    }

    public void addMessage(Message message){
        try {
            synchronized (messagesList) {
                while (messagesList.size() == 32000) {
                    try {
                        messagesList.wait();   //Important
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                messagesList.add(message);
//                System.out.println("added Message");
                messagesList.notifyAll();  //Important
            } //synchronized ends here : NOTE
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void write(SelectionKey currentKey) throws IOException {
        SocketChannel channel = (SocketChannel) currentKey.channel();
        currentKey.interestOps(SelectionKey.OP_READ);
    }

    public byte[] createMes(String key, byte[] ...Message){
        int length = 0;
        for ( int i = 0; i<Message.length; i++){
            length += Message[i].length;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        for (int i = 0; i < Message.length; i++){
            byteBuffer.put(Message[i]);
        }

        byte[] data = byteBuffer.array();
        short lengthData = 0;

        if (data.length > Short.MAX_VALUE){
            lengthData = Short.MAX_VALUE;
            data = Arrays.copyOf(data,lengthData);
        }else{
            lengthData = (short) data.length;
        }
        ByteBuffer dataBuffer = ByteBuffer.allocate(length+4);
        dataBuffer.put(key.getBytes()).putShort(lengthData).put(data);
        return dataBuffer.array();
    }



    public boolean isLogin(){
        return login;
    }


    public boolean isStart() {
        return start;
    }

    public void setStart(boolean start) {
        this.start = start;
    }

    public class ClientProcessData implements Runnable{
        private ListentEvent listen;


        public void setOnEvent(ListentEvent event){
            this.listen = event;
        }

        public Queue<byte[]> Process(byte[] data){
            LinkedList<byte[]> Messange = new LinkedList<>();
            while(data.length >4){
                String IdField = new String(data, 0,2);
                short LengthField = ByteBuffer.wrap(data, 2, 2).getShort();
                byte[] dataField = Arrays.copyOfRange(data, 4, 4+LengthField);
                Messange.add(dataField);

                if (4+ LengthField > data.length) break; else
                    data = Arrays.copyOfRange(data,4+LengthField, data.length);

            }
            return Messange;
        }

        @Override
        public void run() {

            while (true) {
                synchronized (messagesList) {
                    while (messagesList.isEmpty()) {
//                    LOGGER.info(Thread.currentThread().getName() + " Empty        : waiting...\n");
                        try {
                            messagesList.wait();  //Important
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    Message message = messagesList.remove();

                    byte[] TVL = message.getByteMess();

                    String Header = new String(TVL,0,2);
//                    if (Header != null)System.out.println(Header);
                    short mesLength = ByteBuffer.wrap(TVL, 2, 2).getShort();
                    byte[] data = Arrays.copyOfRange(TVL, 4, 4+ mesLength);

                    LinkedList<byte[]> messageDecode = (LinkedList) Process(data);

                    switch(Header){
                        case LOGIN_TRUE:
                            login = true;
                            if (listen != null){
                                this.listen.reciverLogined();
                            }
                            break;
                        case LOGIN_FALSE:
                            login = false;
                            if (listen != null){
                                this.listen.reciverLoginFalse();
                            }
                            break;
                        case REGISTER_TRUE:

                            if (listen != null){
                                this.listen.reciverRigister();
                            }
                            break;
                        case SHAPS:
                            genShaps = new String (messageDecode.poll()).trim();
                System.out.println(genShaps);
                            if (listen != null){
                                this.listen.reciverGenShap(genShaps);
                            }
                            break;
                        case START:
                            String playerID = new String(messageDecode.poll()).trim();
                            String competitor = new String(messageDecode.poll()).trim();

                            start = true;
                            if (listen != null){
                                this.listen.reciverStated(playerID, competitor);
                            }
                            break;
                        case MOVE_ROTATE:
                            listMove.add(new String(messageDecode.poll()).trim());
//                            System.out.println(listMove.peek());
                            if (listen != null){
                                this.listen.reciverMove(listMove);
                            }
                            break;
                        case GAME_OVER:
                            if (listen != null){
                                this.listen.gameOver();
                            }
                            break;
                    }
                    messagesList.notifyAll();

                }
            }


        }
    }
}

