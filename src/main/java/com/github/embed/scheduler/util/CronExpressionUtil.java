package com.github.embed.scheduler.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronSequenceGenerator;

@Slf4j
public class CronExpressionUtil {


    public static   boolean isValidExpression1(String cronExpression){
        try{
            CronSequenceGenerator cron =
                    new CronSequenceGenerator(cronExpression) ;
        }catch (Exception e){
            log.error(e.getMessage() , e);
            return false ;
        }
        return true;
    }

    public static  CronSequenceGenerator cronExpression1(String cronExpression){
        CronSequenceGenerator cron =
                    new CronSequenceGenerator(cronExpression) ;
        return cron ;
    }


    public  static boolean isValidExpression2(String cronExpression){
        try{
            return CronExpression.isValidExpression( cronExpression ) ;
        }catch (Exception e){
            log.error(e.getMessage() , e);
            return false ;
        }
    }


    public static  CronExpression cronExpression2(String cronExpression){
        return CronExpression.parse(cronExpression) ;
    }

}
