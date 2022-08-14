package com.xxyyzz;

import com.xyz.annotation.TraceMethodInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Hello world!
 *
 */

@Slf4j
public class App 
{
    public static void main( String[] args )
    {
        log.debug( "" );
        System.out.println( "Hello World!" );
        App app = new App();
        app.testNoParams();
        app.testOneParams( "10", 10 );
    }

    @TraceMethodInfo
    public void testNoParams() {

    }

    @TraceMethodInfo
    public int testOneParams( String input, int x ) {
        int l = input.length();
        System.out.println( "length: " + l + ", int value: " + input );
        if( l == 0 ) {
            return 100;
        }
        return l;
    }

    private int notAnnotationProcess() {
        return 0;
    }
}
