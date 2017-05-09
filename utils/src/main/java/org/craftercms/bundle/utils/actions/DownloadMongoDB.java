package org.craftercms.bundle.utils.actions;

import com.sun.javafx.fxml.builder.URLBuilder;
import org.craftercms.bundle.utils.Action;
import org.craftercms.bundle.utils.OsCheck;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;

/**
 * Created by cortiz on 4/27/17.
 */
public class DownloadMongoDB implements Action {
    @Override
    public void execute(String[] args) {
        OsCheck.OSType os = OsCheck.getOperatingSystemType();
        String builder="http://downloads.mongodb.org/@OS/mongodb-@OS-x86_64-@VERSION.@EXT";
        switch (os){
            case Windows:
                builder=builder.replaceAll("@OS","win32")
                        .replaceAll("@VERSION","2008plus-ssl-v3.4-latest-signed")
                        .replaceAll("@EXT","msi");
                break;
            case MacOS:
                builder=builder.replaceAll("@OS","osx")
                        .replaceAll("@VERSION","3.4.4")
                        .replaceAll("@EXT","tgz");
                break;
            case Linux:
                builder=builder.replaceAll("@OS","linux")
                        .replaceAll("@VERSION","3.4.4")
                        .replaceAll("@EXT","tgz");
                break;
            default:
                System.out.println("Current OS not supported, please check documentation for installing manually mongodb");
        }

        try {
            URL downloadUrl=new URL(builder.toString());
            try{
                URLConnection connection = downloadUrl.openConnection();
                InputStream input = connection.getInputStream();
                File output=null;
                if(OsCheck.getOperatingSystemType()== OsCheck.OSType.Windows) {
                   output = Paths.get(".", "mongodb.msi").toFile();
                }else{
                   output = Paths.get(".", "mongodb.tgz").toFile();
                }
                OutputStream out = new FileOutputStream(output);
                byte[] buffer= new byte[1024];
                int n;
                long total=0;
                while ((n = input.read(buffer)) != -1){
                    out.write(buffer,0,n);
                    total+=n;
                    System.out.printf("Downloading ... %.1f mb\r",(double)((total/1024)/1024));
                 
                }
                out.flush();
                out.close();
                input.close();
            }catch (IOException ex){
                ex.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void help() {

    }
}
