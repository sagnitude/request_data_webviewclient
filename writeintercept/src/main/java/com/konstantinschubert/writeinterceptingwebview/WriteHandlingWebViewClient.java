package com.konstantinschubert.writeinterceptingwebview;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


public class WriteHandlingWebViewClient extends WebViewClient {

    private final String MARKER = "AJAXINTERCEPT";
    private Map<String, String> ajaxRequestContents = new HashMap<>();


    public WriteHandlingWebViewClient(WebView webView) {
        AjaxInterceptJavascriptInterface ajaxInterface = new AjaxInterceptJavascriptInterface(this);
        webView.addJavascriptInterface(ajaxInterface , "interception");
    }

    public WebResourceResponse shouldInterceptRequest(
            final WebView view,
            WriteHandlingWebResourceRequest request
    ){
        return null;
    }

    @Override
    public final WebResourceResponse shouldInterceptRequest(
            final WebView view,
            WebResourceRequest request
    ) {

        String requestBody = null;
        Uri uri = request.getUrl();
        if (isAjaxRequest(request)){
            requestBody = getRequestBody(request);
            uri = getOriginalRequestUri(request, MARKER);
        }
        WebResourceResponse webResourceResponse =  shouldInterceptRequest(
                view, new WriteHandlingWebResourceRequest(request, requestBody, uri)
        );
        if (webResourceResponse != null) {
            return injectIntercept(webResourceResponse, view.getContext());
        } else {
            return super.shouldInterceptRequest(view, request);
        }
    }

    void addAjaxRequest(String id, String body){
        ajaxRequestContents.put(id, body);
    }

    private String getRequestBody(WebResourceRequest request){
        String requestID = getAjaxRequestID(request);
        return  getAjaxRequestBodyByID(requestID);
    }

    private boolean isAjaxRequest(WebResourceRequest request){
        return request.getUrl().toString().contains(MARKER);
    }

    private String[] getUrlSegments(WebResourceRequest request, String divider){
        String urlString = request.getUrl().toString();
        return urlString.split(divider);
    }


    private String getAjaxRequestID(WebResourceRequest request) {
        return getUrlSegments(request, MARKER)[1];
    }

    private Uri getOriginalRequestUri(WebResourceRequest request, String marker){
        String urlString = getUrlSegments(request, marker)[0];
        return Uri.parse(urlString);
    }

    private String getAjaxRequestBodyByID(String requestID){
        String body = ajaxRequestContents.get(requestID);
        ajaxRequestContents.remove(requestID);
        return body;
    }

    private WebResourceResponse injectIntercept(WebResourceResponse response, Context context){
        String encoding = response.getEncoding();
        String mime = response.getMimeType();
        InputStream responseData = response.getData();
        InputStream injectedResponseData = injectInterceptToStream(
                context,
                responseData,
                mime,
                encoding
        );
        WebResourceResponse resp = new WebResourceResponse(mime, encoding, injectedResponseData);
        resp.setResponseHeaders(response.getResponseHeaders());
        return resp;
    }

    private InputStream injectInterceptToStream(
            Context context,
            InputStream is,
            String mime,
            String charset
    ) {
        try {
            byte[] pageContents = Utils.consumeInputStream(is);
            if (mime.equals("text/html")) {
                pageContents = AjaxInterceptJavascriptInterface
                        .enableIntercept(context, pageContents)
                        .getBytes(charset);
            }

            return new ByteArrayInputStream(pageContents);
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }
}