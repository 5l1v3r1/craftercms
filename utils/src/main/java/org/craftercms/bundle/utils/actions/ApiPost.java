package org.craftercms.bundle.utils.actions;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.craftercms.bundle.utils.Action;

/**
 * Created by joseross on 7/18/17.
 */
public class ApiPost implements Action {

    @Override
    public void execute(final String[] args) {
        if(args.length != 2) {
            help();
        } else {
            try {
                System.out.println("Calling '" + args[0] + "' with body: " + args[1]);
                URL url = new URL(args[0]);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                String body = args[1];
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes());
                }
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    while (in.read(buffer) != -1) {
                        System.out.write(buffer);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void help() {
        System.out.println("Executes a HTTP POST request");
        System.out.println("Usage post {url} {body}");
        System.out.println("\t url: full HTTP url");
        System.out.println("\t body: JSON string to include as body of the request");
    }
}
