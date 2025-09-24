package com.github.embed.scheduler.response;

import lombok.Data;

@Data
public class ErrorResponse {

    private String status ;
    private String error ;
    private String message ;
    public ErrorResponse(){

    }
    public ErrorResponse(String status,String error , String message){
        this.status = status ;
        this.error = error ;
        this.message = message ;
    }
    public ErrorResponse(String status, String message){
        this.status = status ;
        this.message = message ;
    }


}
