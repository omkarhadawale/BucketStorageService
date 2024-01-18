
import java.net.InetAddress;
import java.net.SocketAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintStream;

/* -- end of imports -- */
import java.io.PrintWriter;

class S3 {

    private byte []     HOST;
    private byte [] URL;
    private int         PORT;
    private InetAddress Addr;
    private byte [] FileName;
    private int Offset = 0;
    private int Length = 0;
    private int IsLocalOrRemote = -1;
    //private byte [] remoteURL;

    private byte[] httpSuccess = {72, 84, 84, 80, 47, 49, 46, 49, 32, 50, 48, 48, 32, 79, 75};
    private byte[] httpBadRequest = {72, 84, 84, 80, 47, 49, 46, 49, 32, 52, 48, 48, 32, 66, 65, 68, 32, 82, 69, 81, 85, 69, 83, 84};
    private byte[] allContentType = {67, 111, 110, 116, 101, 110, 116, 45, 84, 121, 112, 101, 58, 32, 42, 47, 42};
    private byte[] contentLength = {67, 111, 110, 116, 101, 110, 116, 45, 76, 101, 110, 103, 116, 104, 58, 32};

public static void main(String [] a) {   

  S3 bucket = new S3(Integer.parseInt("1200"));

  bucket.run(0);

}

public S3 (int port) { PORT = port; }           

int parse(byte[] buf) {
        try{
        int j = 0;
        int index = 1;
        int [] indexes = new int[5];
        indexes[0] = 0;
        for (int i = 0; i < buf.length; i++) {
            if (j == 2){
                break;
            }
            if(buf[i]== 13 && buf[i+1]==10)
            {
                indexes[index] = i - 1;
                index +=1;
                indexes[index] = i + 2;
                index +=1;
                j +=1;
            }
        }

        byte[] requestFirstLine = sliceArray(buf, indexes[0], indexes[1]);
        System.out.println(byte2str(requestFirstLine));
        byte[] requestSecondLine = sliceArray(buf, indexes[2], indexes[3]);
        System.out.println(byte2str(requestSecondLine));
        //Check weather request local or remote
        if(checkRequestLocalOrRemote(requestFirstLine) == 0){
            IsLocalOrRemote = 0;
            //Extract FileName
            FileName = extractFileName(requestFirstLine);
            System.out.println(FileName);
        }
        else if(checkRequestLocalOrRemote(requestFirstLine) == 1){
            //Extract URL
            IsLocalOrRemote = 1;
            URL = extractUrl(requestFirstLine);
            System.out.println(byte2str(URL));
        }
        else{
            IsLocalOrRemote = -1;
        }
        //Extract Offset o=111 and &&=38    
        byte [] Offst = extractOffsetOrLength(requestFirstLine, 111, 38);
        Offset = Integer.parseInt(byte2str(Offst));
        System.out.println(Offset);
        
        //Extract Length l=108 " "= 32
        byte [] Len = extractOffsetOrLength(requestFirstLine, 108, 32);
        Length = Integer.parseInt(byte2str(Len));
        System.out.println(Length);

        return 1;
    }
    catch(Exception e){
        System.out.println(e);
        return 0;
    }
    }

    int dns(int X) {

        //Setting host from URL
        int end_index = 0;
        for (int i = 0; i < URL.length; i++) {
        if(URL[i] == 47){
            end_index = i - 1;
            break;
        }
    }   
    HOST = sliceArray(URL,0,end_index);
    System.out.println(byte2str(HOST));
    return 0;
    }

    int run(int X) {
        ServerSocket s0 = null;
        Socket s1 = null;
        byte[] b0 = new byte[1024]; 
        
        try {
            s0 = new ServerSocket(PORT);
            while (true) {
                s1 = s0.accept();
                InputStream inputStream = s1.getInputStream();
                inputStream.read(b0);
                Offset = Length = 0;
                FileName = null;
                HOST = null;
                IsLocalOrRemote = -1;
                if (parse(b0) == 1){
                    System.out.println("Data parsed... FileName, Offset & Lenght extracted  ");
                }
                else{
                    System.out.println("Error in parsing data"); 
                }
                //Set host
                dns(0);
                //Handle user request
                handleRequest(s1);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void handleRequest(Socket socket) {
        try {
            
            OutputStream outputStream = socket.getOutputStream();

            if(IsLocalOrRemote == 0){
            handleLocalRequest(outputStream);
            }
            else if(IsLocalOrRemote == 1){
                handleRemoteRequest(outputStream);
            }
            else{
                System.out.println("Invalid Request");
            }
            socket.close(); 
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleLocalRequest(OutputStream outputStream){
        try {
            PrintStream printStream = new PrintStream(outputStream);
            File file = new File(byte2str(FileName));
            
            if (file.exists()) {
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[1024]; 
            int bytesRead;
            if(Offset + Length > file.length() ) {
                printStream.println(byte2str(httpBadRequest));
                printStream.println(byte2str(allContentType));
                String errorMessage = "Invalid Offset or Length or both";
                printStream.println(byte2str(contentLength) + errorMessage.length());
                printStream.println();
                outputStream.write(errorMessage.getBytes());
            }
            else if(Offset >= 0  && Length > 0 ){
                printStream.println(byte2str(httpSuccess));
                printStream.println(byte2str(allContentType));
                byte[] slicedArray = new byte[Length];
                printStream.println(byte2str(contentLength) + slicedArray.length);
                printStream.println();
                

                fileInputStream.skip(Offset);
                while ((bytesRead = fileInputStream.read(slicedArray)) != -1) {
                outputStream.write(slicedArray, 0, bytesRead);
            }
            }
            else{
            printStream.println(byte2str(httpSuccess));
            printStream.println(byte2str(allContentType));
            printStream.println(byte2str(contentLength) + file.length());
            printStream.println();
                
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileInputStream.close();
        }
        } else {
            
            String errorMessage = "File not found";
            outputStream.write(errorMessage.getBytes());
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    }

    private void handleRemoteRequest(OutputStream outputStream) {
        try {
        PrintStream printStream = new PrintStream(outputStream);
        Socket remoteSocket = new Socket(byte2str(HOST),443);
        InputStream remoteInputStream = remoteSocket.getInputStream();
        
        printStream.println(byte2str(httpSuccess));
        printStream.println(byte2str(allContentType));
        printStream.println("GET /wiresharklabs/alice.txt HTTP/1.1");
        printStream.println("Host: gaia.cs.umass.edu");
        printStream.println("Transfer-Encoding: chunked");
        printStream.println(); 
       
        byte[] buffer = new byte[1024];
        int bytesRead;
        remoteInputStream.read(buffer);
        System.out.println(byte2str(buffer));
        
        
        remoteSocket.close();
        
    } catch (IOException e) {
        System.out.println(e);
        System.out.println("-------------------------------------");
        e.printStackTrace();
    }
    }  

    
    private byte[] extractUrl(byte[] requestLine){
        if(requestLine.length == 0){
            return requestLine;
        }
        int start_index = 0;
        int end_index = 0;
        int c = 0;
        for (int i = 0; i < requestLine.length; i++) {
        
        if(c == 1 && (requestLine[i] == 32 || requestLine[i]==38)){

            end_index = i - 1;
            break;
        }
        if(i==requestLine.length){
        break;
        }
        if(requestLine[i]== 104 && requestLine[i+1] == 120 && requestLine[i+2]==58 && requestLine[i+3]==47&&requestLine[i+4]==47){
            start_index = i + 5;
            c +=1;
        }
    }   
    return sliceArray(requestLine,start_index, end_index);
    }
    
    private static byte[] extractOffsetOrLength(byte [] requestLine,int a, int b){
        if(requestLine.length == 0){return requestLine;}
        //Extract Length
        int start_index = 0;
        int end_index = 0;
        int c = 0;
        for (int i = 0; i < requestLine.length; i++) {
        if(requestLine[i]== a && requestLine[i+1] == 120 && requestLine[i+2] == 61){
            start_index = i + 3;
            c +=1;
        }
        if(c == 1 && requestLine[i] == b){
            end_index = i - 1;
            break;
        }
    }   
    return sliceArray(requestLine,start_index, end_index);
    }

    private static byte[] extractFileName(byte[] requestLine){
        if(requestLine.length == 0){
            return requestLine;
        }
        int start_index = 0;
        int end_index = 0;
        int c = 0;
        for (int i = 0; i < requestLine.length; i++) {
        
        if(c == 1 && (requestLine[i] == 32 || requestLine[i]==38)){
            end_index = i - 1;
            break;
        }
        if(i==requestLine.length){
        break;
        }
        if(requestLine[i]== 47 && requestLine[i+1] == 47 ){
            start_index = i + 2;
            c +=1;
        }
    }   
    return sliceArray(requestLine,start_index, end_index);
    }

    private static int checkRequestLocalOrRemote(byte [] b){
        for (int i = 0; i < b.length; i++) {
            if(b[i] == 47 && b[i+1] == 102 && b[i+2] == 120 && b[i+3] == 58){
                System.out.println("Request is local");
                return 0;
            }
            else if(b[i] == 47 && b[i+1] == 104 && b[i+2] == 120 && b[i+3] == 58){
                System.out.println("Request is remote");
                return 1;
            }
            }
            return -1;
    }

    private static byte[] sliceArray(byte[] b, int i, int j){
        if(b.length == 0){
            return b;
        }
        byte [] slicedArray = new byte[j - i + 1];
        int c = 0;
        for(int a = i; a <= j; a++){
            slicedArray[c] = b[a]; 
            c += 1;
        }
        return slicedArray;
    }

    private static String byte2str(byte[] b){return byte2str(b,0,0);}
    
    private static String byte2str(byte[] b, int i, int j) {
        if(i == 0 && j == 0){
            return new String(b);
        }
        else{
            return new String(sliceArray(b, i, j));
        }
    }

} 
     