package javaapplication18 ;

import java.util.*;
import java.io.*;
import java.net.*;
import java.io.File ;
import java.io.BufferedReader ;
import java.lang.Integer ;
import java.util.ArrayList;
import java.math.* ;
import java.security.MessageDigest ;
import java.text.SimpleDateFormat;
/*import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;*/

public class JavaApplication18 implements Runnable {
  ServerSocket server = null;
  static String  root; 
  static String Method = "" ; // 方法，GET or POST or HEAD ......
  static String Data = "" ; // 除了第一行(request)以外的訊息 
  String[] map ={"jpg=image/jpeg", "gif=image/gif", "zip=application/zip", "pdf=application/pdf", "xls=application/vnd.ms-excel", "ppt=application/vnd.ms-powerpoint", "doc=application/msword", "htm=text/html", "html=text/html", "css=text/plain", "vbs=text/plain", "js=text/plain", "txt=text/plain", "java=text/plain"};
  FileWriter log = new FileWriter("WebServer.log", true); // 紀錄連線資訊
  
  public static void main(String args[]) throws Exception {
    int serverPort = 80; 
    String rootPath = "" ; 
    if (args.length >= 2) serverPort = Integer.parseInt(args[1]);
    if (args.length >= 1) rootPath = args[0]; // 空的
    new JavaApplication18(serverPort, rootPath); // 建立 ServerSocket 物件並佔領 port (預設為 80).
  } // main
	
  public JavaApplication18(int pPort, String pRoot) throws IOException { 
        
    server = new ServerSocket(pPort); // 建立 ServerSocket 物件並佔領 port (預設為 80).
    File rootDir = new File(pRoot); // 建立一新檔案，返回的路徑才會正確
    root = rootDir.getCanonicalPath(); // ROOT 返回的是伺服器檔案的規範化絕對路徑
    System.out.println("port = "+pPort+ "\n"+"root = "+root);
    run(); // 開始
    
  }
    
  public void run() {
    try {
         
      Socket socket = server.accept();       // 接受瀏覽器的連線。注意: accept() 是一個等待型呼叫，會一直等到有連線進來才完成執行。
      Thread service = new Thread(this);     // 建立一個新的 WebServer 執行緒。
      service.start();                      // 啟動它以以便處理下一個請求。
      DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // 取得傳送資料的輸出檔。
      BufferedInputStream in = new BufferedInputStream(socket.getInputStream());     // 取得接收資料的輸入檔。
      BufferedReader br = new BufferedReader( new InputStreamReader(in), 4096  ) ; // 使用BufferedReader，讀取檔案時較方便，會記錄讀取至哪!!!!!!!!!
      
      String request = request(br) ;     // 讀取瀏覽器傳來的請求訊息、request。
      response(socket, request, out, br );        // 回應訊息給對方的瀏覽器、response。
      Method = "" ;
      Data = "" ; // 重整免得後面request加上去        
      socket.close();                       // 關閉該連線。
       
    } catch (Exception e) { e.printStackTrace(); }
    
    Method = "" ;
    Data = "" ; // 重整免得後面request加上去   
  } // run
  
  String request(BufferedReader br) {   // 分析request訊息
    String request = "" ;
    try {    
      request = br.readLine();
      String line = "";
      while ( ( line = br.readLine() ).length() != 0 ) { 
         
        Data += line + "\n" ; // Data = Data + line
      } // while         
    } catch (Exception e) {}
    
    return request ;
  } // request

  void response(Socket socket, String request, DataOutputStream out, BufferedReader br ) throws Exception { 
        
    try {  
      
      String path = innerText(request, "HTTP/",br ).trim(); // 取得文件名稱 trim():去頭尾的空白 
      path = java.net.URLDecoder.decode(path, "UTF-8");  	// 將檔案路徑解碼以 Unicode 方式解碼。    
      log.write((new Date()).toString()+"\t,"+socket.getRemoteSocketAddress()+"\t,"+path+"\r\n"); // getRemoteSocketAddress():取得客戶端 IP & PORT
      log.flush(); // flush()
      String fullPath = root+path;                // 將路徑轉為絕對路徑。 C:\Users\User\Documents\NetBeansProjects\JavaApplication18/index.html
      if (fullPath.indexOf(".")<0) {
            fullPath += "index.html";	
            path = "/index.html" ;
      } // if 
      File file = new File(fullPath);   // file為硬碟的檔案   
      Long lastModified = file.lastModified(); // 檔案最後修改的時間
      Date WriteDate = new Date(lastModified); // 檔案最後修改的時間
      path = path.replace("/","") ; // 將前面的"/"去掉方便301
             
       // --------------以下為Conditional Get------------------    
      if( (Method.equals( "GET" ) || Method.equals( "HEAD" ) || Data.indexOf("If-Match:") != -1 )  || Data.indexOf("If-Unmodified-Since:") == -1         
           || file.exists() || Data.indexOf("If-None-Match:") != -1 || Data.indexOf("If-Range:") != -1 )  { 
 
           String ETag = GetEtag(path) ; 
           if ( IfMatchToETag(Data,ETag) && Data.indexOf("If-Match:") != -1  ) { // IF-MATCH
                Method = "GET304" ;
 
           } // if       
           else {        
                                   
                 Method = "GET412" ; 
                 if ( Data.indexOf("If-Match:") == -1 && IfUnModified( Data, WriteDate )  ) { // IF-UnModified
                       
                       Method = "GET304" ; // 進來代表Match不存在且檔案更改時間較早，不須更新
                     
                 } // if                  
                 else if ( Data.indexOf("If-Unmodified-Since:") == -1 && Data.indexOf("If-Match:") == -1) { // 進來代表以上兩個都沒有資料

                      if ( IfNoneMatchToETag( Data, ETag ) ) {                // If-None-Match 
                            Method = "GET304" ;
                         
                      } // if 
                      else {      
                          Method = "GET200" ;          // IF-Modified
                          if ( IfModified( Data, WriteDate ) && Data.indexOf("If-None-Match:") == -1) {  // 進來代表NoneMatch不存在且日期正確，不須更新
                            Method = "GET304" ;
                          } // if 
                          else {                  
                            Method = "GET200" ;  
                          } // else 
                          
                      } // else  
                      
                 } // else if 
               
          } // else            
          if ( IfRangeETag( Data, ETag ) && Data.indexOf("If-Range:") != -1 ) {   // If-Range

                if ( RangeOk(Data,file) ) { // file為檔案
                      Method = "GET206" ;  //range給的範圍是正常的
                } // if        
                else {
                     Method = "GET416" ;
                } // else 
                
                
          } // if  
          else {
                 
            if ( IfRangeDate( Data, WriteDate ) ) Method = "GET416" ;
            else Method = "GET206" ;
          } // else       

      } // if 

      // ----------------判別寫入之前的Exception----------------------- 

      if ( Method.equals("Bad Request") ) {
            throw new Exception("Bad Request !") ;   // 400
      } // if   
      else if ( request.indexOf( "HTTP/1.1" ) == -1 ) { // 第一行沒有訊息返回-1，505  

            throw new Exception( "HTTP Version Not Supported !" ) ;
           
      } // else if        
      else if ( !file.exists() && !Method.equals("DELETE") && !Method.equals("DELETENoFound") && !Method.equals("PUT") && !Method.equals("PUTexist") ) { // 確保文件存在 DETELE為先刪除，所以要作BOOL判別
            
            throw new Exception("File not found !"); // 404
            
      } // else if      
      else if ( Redirection(path ) && !Method.equals("PUT") ) { // 為需要轉向的網頁，PUT時會先建網頁，所以會直接轉向
            
            if ( path.indexOf( "hl" ) != -1 ) { 
                  throw new Exception( "Move Permanently !hl" ) ; // 檔案有hl轉向的頁面有超連結
            } // if         
            else {
                  throw new Exception( "Move Permanently !" ) ;           
            } // else      
            
      } // else if  
      //-----------------以下為取得瀏覽器(POST)輸入的資料-------------------
      String InputData = "" ;
      if ( Method.equals( "POST" ) ) {
            InputData = PostData( br ) ; 
            if( !CorrectCookie(InputData) ) {  
                  throw new Exception( "Not Acceptable !" ) ;   // 不正確的登入訊息 406          
            } // if   
            
      } // if  
    
      // ----------------以下為寫入Response------------------     
      System.out.println("========response()==========");   // 為協定原有的標籤
      if ( Method.equals( "PUT" )  ) {
            output(out, "HTTP/1.1 201 Created");  
      } // if       
      else if ( Method.equals("DELETENoFound") ) {
            output(out, "HTTP/1.1 204 No Content");        
      } // if       
      else if ( Method.equals("GET304") ) {             // Condition GET
            output(out, "HTTP/1.1 304 Not Modified");             
      } // else if  
      else if ( Method.equals("GET412") ) {           // Condition GET
            output(out, "HTTP/1.1 412 Precondition Failed");             
      } // else if   
      else if ( Method.equals("GET416")) {                                    // If-Range
             output(out, "HTTP/1.1 416 Range Not Satisfiable");                      
      } // else if       
      else if ( Method.equals("GET206") ) {
            output(out, "HTTP/1.1 206 Partial Content"); 
      } // else if       
      else { 
            output(out, "HTTP/1.1 200 OK");                       // 傳回成功訊息，包含Putexist
      } // else            
         
      output(out, "Content-Type: "+type(fullPath));      	// 傳回檔案類型
      output(out, "Content-Length: " + file.length());   	// 傳回內容長度
      output(out, "Server: Test "); 
      if ( Method.equals("POST") && InputData != "" )WriteCookie( InputData, out ) ;  // 沒輸入東西就不設定Cookie
      output(out, "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.100 Safari/537.36") ;
      output(out, "Date: " + (new Date()).toString() ) ;
      if ( Method.equals("GET416") || Method.equals("GET206") ) output( out, "Content-Range: " + "bytes " + ContentRange(Data) + "/" + file.length() ) ;  // Content-Range
      if( !Method.equals( "DELETE" ) && !Method.equals("DELETENoFound") && !Method.equals( "PUT" ) && !Method.equals( "PUTexist" )  ) { // ETag的部分，PUT未設定etag         
            String ETag = GetEtag(path) ;    
            output(out, "ETag: " + ETag ) ; // GET && POST 才有          
      } // if       
      output(out, "Last-Modified: " + WriteDate ) ; // 檔案最後的修改時間，若檔案相同就不需做更改
      output(out, "");                                  	// 空行代表標頭段結束 
      
      if (Method.equals( "POST" )) fullPath = root+ "/Success.html" ;
      // -----------------以下為上傳檔案；index.html------------------------ 
      if ( !Method.equals("DELETENoFound") && !Method.equals( "DELETE" ) && !Method.equals("GET304") && !Method.equals("PUT") && !Method.equals("GET206") ) { // DELETE後的檔案不存在，304不需要上傳檔案
            byte[] buffer = new byte[4096];
            FileInputStream is = new FileInputStream(fullPath); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 1, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while    

            is.close() ;
      } // if  
      else if ( Method.equals("GET206") ) {
          String range = ContentRange(Data) ;              
          FileInputStream is = new FileInputStream(fullPath);    

          if ( range.indexOf("-") != -1 ) {  // 有"-" 
                
            int i =  range.indexOf("-") ;   
            String front = range.substring( 0, i ); // EX: a-b，a的部分
            String back = range.substring( i+1 );   //  b的部分，將字串從索引號為i+1開始擷取，一直到字串末尾。
            byte[] buffer = new byte[4096];            
            is.read(buffer, 0 , 4096 ) ; // 將buffer讀入IS資料的0~4096
            out.write(buffer, Integer.valueOf(front), Integer.valueOf(back)-Integer.valueOf(front)-1 ); // 將指定字節組中(BUFFER)從front開始寫入(back-front)數量的字元
                     
          } // if  
          else {        
                
                byte[] buffer = new byte[4096];     
                is.read(buffer, 0 , 4096 ) ; // 將buffer讀入IS資料的0~4096
                out.write( buffer,Integer.valueOf(range), 1 ) ; // 從buffer的range位址開始讀進1字元
                
          } // else         
          is.close() ;        
      } // else if   */    
      // -----------------以上為上傳檔案；index.html------------------------         
      out.flush();
      out.close() ;        
    } catch (Exception e) {  //  !!!!!!!!!!先將Reponse寫回輸出檔再開啟檔案!!!!!!!!!!!!!!
          
      String space = "" ;      
      if( (e.toString().indexOf("Bad Request !") != -1 ) && e != null && !space.equals(e.getMessage()) ) {
            System.out.println("========response()==========");          
            output(out, "HTTP/1.1 400 " + e.getMessage()); // e.getMessage()為例外情況的訊息
            output(out, "Server: Wrong ");   
            output(out, "Date: " + (new Date()).toString() ) ;     
            output(out, "");      // 空行代表標頭段結束，規定!!     
            Status( out, 400 ) ; // !!!!!!!!!!!先將Reponse寫回輸出檔再開啟檔案!!!!!!!!!!!!!!
            
      } // else if
      else if ( (e.toString().indexOf("File not found !") != -1 ) && e != null && !space.equals(e.getMessage()) ) {        
            System.out.println("========response()==========");          
            output(out, "HTTP/1.1 404 " + e.getMessage()); 
            output(out, "Server: Wrong ");   
            output(out, "Date: " + (new Date()).toString() ) ;     
            output(out, "");     
            Status( out, 404 ) ; // 顯示錯誤狀態碼頁面              
      } // else if       
      else if ( (e.toString().indexOf("HTTP Version Not Supported !") != -1 ) && e != null && !space.equals(e.getMessage()) ) {        
            System.out.println("========response()==========");          
            output(out, "HTTP/1.1 505 " + e.getMessage()); 
            output(out, "Server: Wrong ");   
            output(out, "Date: " + (new Date()).toString() ) ;     
            output(out, "");     
            Status( out, 505 ) ; // 顯示錯誤狀態碼頁面           
      } // else if       
      else if( (e.toString().indexOf("Move Permanently !") != -1 ) && e != null && !space.equals(e.getMessage()) ) {
            
            System.out.println("========response()==========");          
            output(out, "HTTP/1.1 301 " + e.getMessage()); // e.getMessage()為例外情況的訊息
            output(out, "Server: Wrong ");   
            output(out, "Date: " + (new Date()).toString() ) ;
            if( (e.toString().indexOf("Move Permanently !hl") != -1 ) && e != null && !space.equals(e.getMessage() ) ) output(out, "Location: 301MovePermanently.html"  ) ; // 會自動跳到主頁; // 會有超連結再跳到主頁
            else output(out, "Location: index.html"  ) ; // 會自動跳到主頁
            output(out, "");      // 空行代表標頭段結束，規定!!     
         
      } // else if   
       else if( (e.toString().indexOf( "Not Acceptable !") != -1 ) && e != null && !space.equals(e.getMessage()) ) { // 錯誤的登入訊息
            
            System.out.println("========response()==========");          
            output(out, "HTTP/1.1 406 " + e.getMessage()); // e.getMessage()為例外情況的訊息
            output(out, "Server: Wrong ");   
            output(out, "Date: " + (new Date()).toString() ) ;
            output(out, "");      // 空行代表標頭段結束，規定!!  
            Status( out, 406 ) ; // 顯示錯誤狀態碼頁面  
         
      } // else if           
      
      out.flush();
      out.close() ;     
    } // catch
    
  } // response
    
    public static String innerText(String pText, String endMark,BufferedReader br ) throws IOException {  // HEAD為判斷是否印request
        
    String beginMark = new String() ;    
    int beginStart = 0 ; // beginStart為Method頭的位置，  
    int beginEnd = 0 ; // beginEnd為Method尾的位置
    int endStart = 0 ; // 找尋字串"endMark"，由beginEnd這位置開始   
    if ( pText.indexOf("GET") < 0 ) { // 判斷Method是否為GET   
          if ( pText.indexOf("POST") < 0 ) { // 判斷Method是否為POST         
                if ( pText.indexOf("PUT") < 0 ) { // 判斷Method是否為PUT                 
                       if ( pText.indexOf("HEAD") < 0 ) { // 判斷Method是否為HEAD                          
                            if ( pText.indexOf("DELETE") < 0 ) { // 判斷Method是否為DELETE                          
                              Method = "Bad Request" ;
                              beginStart = pText.indexOf("/"); // beginStart為"/"的位置，
                            } // if
                            else {  // Delete
                              beginMark = "DELETE" ;
                              beginStart = pText.indexOf("DELETE"); // beginStart為"DELETE"的位置，
                              Method = "DELETE" ;
                              beginEnd = beginStart+beginMark.length()+2; // beginEnd為Method之後的位元
                              endStart = pText.indexOf(endMark, beginEnd); // endStart為 HTTP/1.1的前面     
                              String Filename = pText.substring(beginEnd, endStart ) ; // 此為檔案的名稱(不包含" /"!!!!!!!!)，所以beginEnd要+2 
                              File file = new File( Filename ) ; // 在同資料夾，找檔案名稱就行
                              if ( file.exists() ) { // 假設檔案存在                       
                                    file.delete(); // 刪除檔案
                                    System.out.println( "File has been deleted " ) ;
                              } // if   
                              else {
                                    Method = "DELETENoFound" ; // 原本要刪除時沒檔案
                              } // else      
                            } // else                        
                       } // if
                       else {  // HEAD
                            beginMark = "HEAD" ;
                            beginStart = pText.indexOf("HEAD"); // beginStart為"HEAD"的位置，
                            Method = "HEAD" ;                          
                       } // else                         
                } // if
                else {  // PUT
                   Method = "PUT" ; 
                   /*beginMark = "PUT" ;
                   beginStart = pText.indexOf("PUT"); // beginStart為"PUT"的位置，
                   beginEnd = beginStart+beginMark.length()+2; // beginEnd為Method之後的位元
                   endStart = pText.indexOf(endMark, beginEnd); // endStart為 HTTP/1.1的前面     
                   String Filename = pText.substring(beginEnd, endStart ) ; // 此為檔案的名稱(不包含" /"!!!!!!!!)，所以beginEnd要+2
                   if ( Filename != "" ) { // POSTMAN後面加檔案!!!!!!
                                            System.out.println("11111");
                        File FileExist = new File (Filename) ; // 判斷檔案是否存在               
                         if ( FileExist.exists() ) { // 假設檔案存在
                            FileWriter file = new FileWriter( Filename ) ; // 覆蓋
                            file.write( "File updated!!" ) ;
                            file.flush();
                            file.close();
                            System.out.println( "File Exist ? " + FileExist.exists() + "!!" ) ;
                            Method = "PUTexist" ; // 假設是PUT且存在回傳200非201
                         } // if     
                         else {             // 不存在則新增         
                              System.out.println( "File Exist ? " + FileExist.exists() + "!!" ) ;                         
                              FileExist.createNewFile() ; // 新增資料
                              FileWriter file = new FileWriter( Filename ) ; // 覆蓋
                              file.write( "File has been created!!" ) ;
                              file.flush();
                              file.close();                         
                        }// else
                         
                   } // else */
                               // POSTMAN的Form-Data加檔案!!!!!!                                          
                        String boundary = GetBoundary( Data, br ) ;
                        String PutFilename = PutGetFilename( Data, br) ; // 新的檔案名稱
                        File FileExist = new File (PutFilename) ; // 判斷檔案是否存在  
                        if ( FileExist.exists() ) { // 假設檔案存在
                            System.out.println( "File Exist ? " + FileExist.exists() + "!!" ) ;  
                            Method = "PUTexist" ; // 假設是PUT且存在回傳200非201
                        } // if     
                        else {             // 不存在則新增         
                              System.out.println( "File Exist ? " + FileExist.exists() + "!!" ) ;                                               
                        }// else                         
                        String OtherDetail = br.readLine() ; // Content-Type: text/plain       
                        OtherDetail = br.readLine() ; // Space line
                        String boundaryTail = boundary+ "--" ;
                        FileWriter PutData = new FileWriter(PutFilename) ;
                        BufferedWriter input = new BufferedWriter( PutData ) ;
                        String line = br.readLine() ; // 開始讀檔
                        while( !line.equals(boundaryTail) ) {
                              input.write(line+"\r\n") ; // 覆蓋檔案
                              line = br.readLine() ; // 檔案內容
                             
                        } // while                       
                        input.close() ;                                    
                } // else       
          } // if
          else {  // POST
                beginMark = "POST" ;
                beginStart = pText.indexOf("POST"); // beginStart為"POST"的位置，
                Method = "POST" ;
          } // else             
    } // if 
    else {   // GET
       beginMark = "GET" ;
       beginStart = pText.indexOf("GET"); // beginStart為"GET"的位置， 
       Method = "GET" ;
       
    } // else         
    System.out.println("=========request()===========");      
    if ( !Method.equals( "HEAD" ) ) { // 不是HEAD印出request
          System.out.println( pText ) ; 
          System.out.println( Data ) ; // 印出除了request以外的第一行
    } // if 
    beginEnd = beginStart+beginMark.length(); // beginEnd為Method之後的位元
    endStart = pText.indexOf(endMark, beginEnd); // 找尋字串"endMark"，由beginEnd這位置開始
    if (endStart < 0) return null;
    
    return pText.substring(beginEnd, endStart ); // substring:擷取字串 beginEnd開始，endStart結束，回傳檔案名稱
    
  } // innerText  

  public static String ContentRange( String request ) throws Exception {   // 回傳Response的ConTent-Range
        
        int RangeStart = request.indexOf("Range: bytes=")+ 13 ; 
        int RangeEnd = request.indexOf("\n", RangeStart ) ; // Range的結尾部分
        String RangeDetail = request.substring(RangeStart, RangeEnd) ; // 切割出來為Range的要求範圍 EX: 100-300 or EX:200

        return RangeDetail ;
  } // ResponseRange      
    
  public static Boolean RangeOk( String request, File file ) throws Exception { // 判斷要求的Range合理
        int Filesize = (int)file.length() ; // 檔案大小
        int RangeStart = request.indexOf("Range: bytes=") + 13 ; 
        int RangeEnd = request.indexOf("\n", RangeStart ) ; // Range的結尾部分
        String RangeDetail = request.substring(RangeStart, RangeEnd) ; // 切割出來為Range的要求範圍 EX: 100-300 or EX:200

        if (RangeDetail.indexOf("-") != -1 ) { //EX: 100-300
   
            int largeStart = request.indexOf("-", RangeStart) ;  // "-"的位置
            String largeNum = request.substring(largeStart+1, RangeEnd) ; // 最大的數字

            if ( Filesize < Integer.valueOf(largeNum ) ) return false ;  // 假設輸入的數字超過檔案大小                
        } // if        
        else {                            // EX:200
     
            if ( Filesize < Integer.valueOf(RangeDetail ) ) return false ;  // 假設輸入的數字超過檔案大小
            
        } // else  
        
        return true ;
        
  } // RangeOk      
  public static boolean  IfUnModified( String request, Date WriteDate ) throws Exception {

        if ( Data.indexOf("If-Unmodified-Since:") == -1 ) return false ;
        int i = request.indexOf("\n", 0 ) ; // 從request的0開始找到換行
        String IfUmDate = request.substring(0, i) ; // If-Unmodified-Since: Mon Sep 17 10:45:13 CST 2019      
        IfUmDate = IfUmDate.replace( "If-Unmodified-Since: ", "" ) ;  
        if ( IfUmDate.compareTo(WriteDate.toString()) > 0 && Data.indexOf("If-Unmodified-Since:") != -1 ) { // 回傳-1 檔案的更新時間比較晚代表需要更新    
  
            return true ;  
        } // if 
 
      return false ;
      
  } // Modified  
   
  public static boolean IfModified( String request, Date WriteDate ) throws Exception {
        
      if ( request.indexOf("If-Modified-Since: "+ WriteDate ) == -1 ) { // 不存在ETag的數字，代表不相同
         return false ;   
      } // if       
        return true ;
        
  } // Modified  
  
  public static boolean IfRangeETag( String request, String ETag ) throws Exception { // Etag版

      if ( request.indexOf("If-Range: "+ ETag ) == -1 && request.indexOf("Range:") != -1 ) { // 不存在ETag的數字，代表不相同
         return false ;   
      } // if       
       return true ;        
        
  } // IfRange 
  
   public static boolean IfRangeDate( String request, Date WriteDate ) throws Exception { // Date版

        if ( Data.indexOf("If-Range: ") == -1 ) return false ;
        int i = request.indexOf("\n", 0 ) ; // 從request的0開始找到換行
        String IfUmDate = request.substring(0, i) ; // If-Unmodified-Since: Mon Sep 17 10:45:13 CST 2019      
        IfUmDate = IfUmDate.replace( "If-Range: ", "" ) ;  
        if ( IfUmDate.compareTo(WriteDate.toString()) > 0 && Data.indexOf("If-Range: ") != -1 ) { // 回傳-1 檔案的更新時間比較晚代表需要更新    
            return true ;  
        } // if 
 
      return false ;
      
  } // IfRange   
  
  public static boolean IfMatchToETag( String request, String ETag ) throws Exception {
        
      if ( request.indexOf("If-Match: "+ ETag ) == -1 ) { // 不存在ETag的數字，代表不相同
         return false ;   
      } // if       
        return true ;        
        
  } // IfMatchToETag      
  public static boolean IfNoneMatchToETag( String request, String ETag ) throws Exception { // 判斷if none match & ETAG 是否一樣
       
      if ( request.indexOf("If-None-Match: "+ETag) == -1 ) { // 不存在ETag的數字，代表不相同
         return false ;   
      } // if       
        return true ;
  } // GetIfNoneMatch    
  
  public static String GetEtag( String filename ) throws Exception {
        
       FileReader fr = new FileReader(filename);       
       BufferedReader br = new BufferedReader(fr);
       String str = "" ; // 檔案的內容 
       String line = "" ;
       while( (line = br.readLine() ) != null ) {
             str = str + line ;   
       } // while   
      if ( filename == "" ) return filename ; 
      else return getMD5( str ) ; // 檔案的內容，經過亂碼處理      
      
  } // GetEtag      
  public static String GetBoundary( String request, BufferedReader br ) throws IOException { //PUT用
             
        String boundary = br.readLine() ; //--------------------------641188853579534289364634
        return boundary ;
        
  } // GetBoundary()  
  
  public static String PutGetFilename( String request, BufferedReader br ) throws IOException { //PUT用
        
        String PutData = br.readLine() ; // Content-Disposition:......
        String filename = PutData.substring(PutData.indexOf("filename") ) ; // filename="new.txt"
        filename = filename.replace("filename=", "" ) ; // "new.txt"
        filename = filename.replace("\"", "" ) ; // new.txt"
        filename = filename.replace("\"", "" ) ; // new.txt
        return filename ;
  } // PutGetFilename() 
  
  boolean Redirection( String path ) throws IOException { // 301重新導向
        
       FileReader fr = new FileReader("301page.txt");       // 開啟紀錄需轉向的txt檔 
       BufferedReader br = new BufferedReader(fr);
       String str = "" ;
       String line = "" ;
       while( (line = br.readLine() ) != null ) {
             str = str + line ;   // 讀取紀錄需轉向的網址
       } // while      
       if ( str.indexOf(path) != -1 ) {
             return true ;
       } // if   
      br.close();
      fr.close(); 
      return false ;  
      
  } // Redirection  
  
  public static String getMD5(String str ){ // MD5convertertoString 
        String ret = null ;
        if (str=="" ) return "" ;
        try {
        MessageDigest md = MessageDigest.getInstance("MD5") ;
        md.update(str.getBytes() ) ;
        ret = new BigInteger(1, md.digest() ).toString(16);
        } catch( Exception e ) {
          e.printStackTrace() ;
        } // catch  
        return ret ;
  } // getMD5      
  
  boolean CorrectCookie( String InputData ) throws Exception { // 判斷帳號密碼是否正確
 
       FileReader fr = new FileReader("UserDetail.txt");       // 開啟紀錄需轉向的txt檔 
       BufferedReader br = new BufferedReader(fr);
       String str = "" ;
       String line = "" ;
       while( (line = br.readLine() ) != null ) {
             str = str + line ;   // 讀取紀錄需轉向的網址
       } // while      
       if ( str.indexOf(InputData) != -1 ) {
             br.close();
             fr.close();             
             return true ;
       } // if   
      br.close();
      fr.close(); 
      return false ;    
           
  } // CorrectCookie      
  void WriteCookie( String InputData, DataOutputStream out ) throws Exception { // 寫Cookie回去且辨認身分
        
     String value = "" ;
     for( int i = 0 ; i < InputData.length() ; i++ ) {
           
           for ( int j = i ; j < InputData.length() && InputData.charAt(j) != '&' ; j++ ) {             
                  value = value + InputData.charAt(j) ;  
                  i = j ;
           } // for  
           if ( InputData.charAt(i) != '&' ) {// 讀到&要跳過，免得多Output一次
                  output(out, "Set-Cookie: " + value ) ;     // 設定Cookie後，下次request會顯示設定的cookie            
                  value = "" ; // 可能不只一個cookie，需重新設定  
           } // if        
           
     } // for    
        
  } // WriteCookie  
  
  String PostData( BufferedReader br ) throws Exception { // 取得Post的資料
 
      int contentlengthStart = Data.indexOf( "Content-Length:" ); // request裡Content-Length的起始位置
      int contentlengthEnd = contentlengthStart + 16 ; // 此位置為 Content-Length的!!值!!，16為"Content-Length: "的長度
      int contentEnd = Data.indexOf( "Cache-Control:", contentlengthEnd ) - 1 ; // 從contentlength的值，找到值的下行，減二為減掉換行
      String contentNum = Data.substring( contentlengthEnd, contentEnd ) ;     // ContentLength!!!!!   
      char[] Array = new char[ Integer.parseInt(contentNum) ];  // InputData為輸入的數字與密碼 EX:Number=11&Password=11
      br.read(Array,0,Integer.parseInt(contentNum));
      String InputData = new String(Array) ;
      if ( Integer.parseInt(contentNum) == 21) InputData = "" ; // Content-Length:21 為沒有東西，InputData不給東西
      return InputData ;   
      
  } // PostData()      
  
  void Status( DataOutputStream out, int StatusNum ) throws IOException  {
 
        if( StatusNum == 301 ) { // 301 頁面
              
            byte[] buffer = new byte[4096];          
            String page = root + "/301MovePermanently.html" ;     
            FileInputStream is = new FileInputStream(page); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 0, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while                    
            is.close() ; 
                         
        } // if         
        else if( StatusNum == 400 ) { // 400 頁面
              
            byte[] buffer = new byte[4096];          
            String page = root + "/400BadRequest.html" ;     
            FileInputStream is = new FileInputStream(page); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 0, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while                    
            is.close() ; 
                         
        } // if 
        else if ( StatusNum == 404 ) { // 404 頁面
              
            byte[] buffer = new byte[4096];
            String page = root + "/404NoFound.html" ;
            FileInputStream is = new FileInputStream(page); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 0, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while                    
            is.close() ;             
              
        } // else if    
        else if ( StatusNum == 406 ) { // 406 頁面
              
            byte[] buffer = new byte[4096];
            String page = root + "/406NotAcceptable.html" ;
            FileInputStream is = new FileInputStream(page); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 0, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while                    
            is.close() ;             
              
        } // else if           
        else if ( StatusNum == 505 ) { // 505頁面
              
            byte[] buffer = new byte[4096];
            String page = root + "/505VersionNotSup.html" ;
            FileInputStream is = new FileInputStream(page); 
            int len ;
            while ((len = is.read(buffer, 0 , 4096) ) > 0) { 
                  out.write(buffer, 0, len); // 將指定字節組中(BUFFER)從偏移量0開始的LEN個字節寫入
            } // while                    
            is.close() ;            
        } // else if     
    
  } // Status      
  
  String type(String path) {
    String type="***"; // 若無type，設定原始名稱為
    path = path.toLowerCase();
    for (int i=0; i<map.length; i++) {
      String[] tokens = map[i].split("="); // 1分2
      String ext = tokens[0], mime = tokens[1]; // ext 檔案型別 ex: jpg，mime: image/jpeg
      if (path.endsWith("."+ext)) type = mime; // endsWith 為判斷字串最後的字元 
    } // for
    return type;
  } // type
  
  void output( DataOutputStream out, String str ) throws Exception {
    try {
    
      out.writeBytes( str + "\r\n") ; // DataOutputStream 需用bytes寫入，字串(writeChars)會出錯!!!!!
      System.out.println(str) ;
      
    } catch( Exception e ) {}
                 
  } // output
  
} // JavaApplication 18