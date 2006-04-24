/*
 * Copyright 2005-2006 Jerome LOUVEL
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.AbstractChainlet;
import org.restlet.Call;
import org.restlet.component.Component;

import com.noelios.restlet.util.StringTemplate;
import com.noelios.restlet.util.CallModel;

/**
 * Chainlet logging all calls after their handling by the target Restlet.<br/>
 * The current format is similar to IIS 6 logs.<br/>
 * The logging is based on the java.util.logging package.
 * @see <a href="http://www.restlet.org/tutorial#part07">Tutorial: Chainlets and call logging</a>
 */
public class LogChainlet extends AbstractChainlet
{
   /** Obtain a suitable logger. */
   protected Logger logger;

   /** The log template to use. */
   protected StringTemplate logTemplate;

   /**
    * Constructor using the default format.<br/>
    * Default format using <a href="http://analog.cx/docs/logfmt.html">Analog syntax</a>: %Y-%m-%d\t%h:%n:%j\t%j\t%r\t%u\t%s\t%j\t%B\t%f\t%c\t%b\t%q\t%v\t%T
    * @param parent The parent component.
    * @param logName The log name to used in the logging.properties file.
    */
   public LogChainlet(Component parent, String logName)
   {
      super(parent);
      this.logger = Logger.getLogger(logName);
      this.logTemplate = null;
   }

   /**
    * Constructor.
    * @param parent The parent component.
    * @param logName The log name to used in the logging.properties file.
    * @param logFormat The log format to use.
    * @see com.noelios.restlet.util.CallModel
    * @see com.noelios.restlet.util.StringTemplate
    */
   public LogChainlet(Component parent, String logName, String logFormat)
   {
      super(parent);
      this.logger = Logger.getLogger(logName);
      this.logTemplate = new StringTemplate(logFormat);
   }

   /**
    * Handles a call to a resource or a set of resources.
    * @param call The call to handle.
    */
   public void handle(Call call)
   {
      long startTime = System.currentTimeMillis();
      super.handle(call);
      int duration = (int)(System.currentTimeMillis() - startTime);

      // Format the call into a log entry
      if(this.logTemplate != null)
      {
         this.logger.log(Level.INFO, format(call));
      }
      else
      {
         this.logger.log(Level.INFO, formatDefault(call, duration));
      }
   }

   /**
    * Format a log entry using the default format.
    * @param call The call to log.
    * @param duration The call duration.
    * @return The formatted log entry.
    */
   protected String formatDefault(Call call, int duration)
   {
      StringBuilder sb = new StringBuilder();

      // Append the time stamp
      long currentTime = System.currentTimeMillis();
      sb.append(String.format("%tF", currentTime));
      sb.append('\t');
      sb.append(String.format("%tT", currentTime));

      // Append the method name
      sb.append('\t');
      String methodName = call.getMethod().getName();
      sb.append((methodName == null) ? "-" : methodName);

      // Append the resource path
      sb.append('\t');
      String resourcePath = call.getResourceRef().getPath();
      sb.append((resourcePath == null) ? "-" : resourcePath);

      // Append the user name
      sb.append("\t-");

      // Append the client IP address
      sb.append('\t');
      String clientAddress = call.getClientAddress();
      sb.append((clientAddress == null) ? "-" : clientAddress);

      // Append the version
      sb.append("\t-");

      // Append the client name
      sb.append('\t');
      String clientName = call.getClientName();
      sb.append((clientName == null) ? "-" : clientName);

      // Append the referrer
      sb.append('\t');
      sb.append((call.getReferrerRef() == null) ? "-" : call.getReferrerRef().getIdentifier());

      // Append the status code
      sb.append('\t');
      sb.append((call.getStatus() == null) ? "-" : Integer.toString(call.getStatus().getHttpCode()));

      // Append the returned size
      sb.append('\t');
      if(call.getOutput() == null)
      {
         sb.append('0');
      }
      else
      {
         sb.append((call.getOutput().getSize() == -1) ? "-" : Long.toString(call.getOutput().getSize()));
      }

      // Append the resource query
      sb.append('\t');
      String query = call.getResourceRef().getQuery();
      sb.append((query == null) ? "-" : query);

      // Append the virtual name
      sb.append('\t');
      sb.append((call.getResourceRef() == null) ? "-" : call.getResourceRef().getHostIdentifier());

      // Append the duration
      sb.append('\t');
      sb.append(duration);

      return sb.toString();
   }

   /**
    * Format a log entry.
    * @param call The call to log.
    * @return The formatted log entry.
    */
   protected String format(Call call)
   {
      return this.logTemplate.process(new CallModel(call, "-"));
   }

}