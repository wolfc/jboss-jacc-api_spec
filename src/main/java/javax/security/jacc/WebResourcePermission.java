/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package javax.security.jacc;

import java.io.Serializable;
import java.io.ObjectStreamField;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.Permission;
import java.util.TreeSet;
import java.util.StringTokenizer;
import java.util.Iterator;
import javax.servlet.http.HttpServletRequest;

import org.jboss.util.id.SerialVersion;

/** Class for Servlet web resource permissions. A WebResourcePermission is a
 * named permission and has actions.
 * 
 * The name of a WebResourcePermission (also referred to as the target name)
 * identifies the Web resources to which the permission pertains.
 * 
 * Implementations of this class MAY implement newPermissionCollection or
 * inherit its implementation from the super class.
 * 
 * 
 * @author Scott.Stark@jboss.org
 * @author Anil.Saldhana@jboss.org
 * @author Ron Monzillo, Gary Ellison (javadoc)
 * @version $Revision$
 */
public final class WebResourcePermission
   extends Permission
   implements Serializable
{
   /** @since 4.0.2 */
   private static final long serialVersionUID;
   private static TreeSet ALL_HTTP_METHODS = new TreeSet();
   /**
    * @serialField actions String the actions string.
    */
    private static final ObjectStreamField[] serialPersistentFields = { 
        new ObjectStreamField("actions", String.class)
    };

   static
   {
      ALL_HTTP_METHODS.add("GET");
      ALL_HTTP_METHODS.add("POST");
      ALL_HTTP_METHODS.add("PUT");
      ALL_HTTP_METHODS.add("DELETE");
      ALL_HTTP_METHODS.add("HEAD");
      ALL_HTTP_METHODS.add("OPTIONS");
      ALL_HTTP_METHODS.add("TRACE");
      if (SerialVersion.version == SerialVersion.LEGACY)
         serialVersionUID = 141000;
      else
         serialVersionUID = 1;
   }

   private transient URLPatternSpec urlSpec;
   private transient String httpMethodsString;
   private transient TreeSet httpMethods;
   private transient TreeSet httpExceptionList;
   private transient String httpExceptionString;

   /** Creates a new WebResourcePermission from the HttpServletRequest object.
    * 
    * @param request  - the HttpServletRequest object corresponding to the
    * Servlet operation to which the permission pertains. The permission name is
    * the substring of the requestURI (HttpServletRequest.getRequestURI()) that
    * begins after the contextPath (HttpServletRequest.getContextPath()). When
    * the substring operation yields the string "/", the permission is
    * constructed with the empty string as its name. The permission's actions
    * field is obtained from HttpServletRequest.getMethod().
    */
   public WebResourcePermission(HttpServletRequest request)
   {
      this(requestURI(request), request.getMethod());
   }

   /** Creates a new WebResourcePermission with the specified name and actions.

    The name contains a URLPatternSpec that identifies the web resources to which
    the permissions applies. The syntax of a URLPatternSpec is as follows:

    URLPatternList ::= URLPattern | URLPatternList colon URLPattern

    URLPatternSpec ::= null | URLPattern | URLPattern colon URLPatternList

 

    A null URLPatternSpec is translated to the default URLPattern, "/", by the
    permission constructor. The empty string is an exact URLPattern, and may
    occur anywhere in a URLPatternSpec that an exact URLPattern may occur. The
    first URLPattern in a URLPatternSpec may be any of the pattern types, exact,
    path-prefix, extension, or default as defined in the Java Servlet
    Specification). When a URLPatternSpec includes a URLPatternList, the
    patterns of the URLPatternList identify the resources to which the
    permission does NOT apply and depend on the pattern type and value of the
    first pattern as follows:

    - No pattern may exist in the URLPatternList that matches the first pattern.
    - If the first pattern is a path-prefix pattern, only exact patterns matched
    by the first pattern and path-prefix patterns matched by, but different
    from, the first pattern may occur in the URLPatternList.
    - If the first pattern is an extension pattern, only exact patterns that are
    matched by the first pattern and path-prefix patterns may occur in the
    URLPatternList.
    - If the first pattern is the default pattern, "/", any pattern except the
    default pattern may occur in the URLPatternList.
    - If the first pattern is an exact pattern a URLPatternList must not be
    present in the URLPatternSpec. 

    The actions parameter contains a comma seperated list of HTTP methods. The
    syntax of the actions parameter is defined as follows:

    HTTPMethod ::= "GET" | "POST" | "PUT" | "DELETE" | "HEAD" |
    "OPTIONS" | "TRACE"
    
    HTTPMethodExceptionList ::= exclaimationPoint HTTPMethodList
    HTTPMethodList ::= HTTPMethod | HTTPMethodList comma HTTPMethod 
    HTTPMethodSpec ::= null | emptyString | HTTPMethodExceptionList | HTTPMethodList
 

    If duplicates occur in the HTTPMethodSpec they must be eliminated by the
    permission constructor.

    A null or empty string HTTPMethodSpec indicates that the permission applies
    to all HTTP methods at the resources identified by the URL pattern.

    @param name - the URLPatternSpec that identifies the application specific web
    resources to which the permission pertains. All URLPatterns in the
    URLPatternSpec are relative to the context path of the deployed web
    application module, and the same URLPattern must not occur more than once
    in a URLPatternSpec. A null URLPatternSpec is translated to the default
    URLPattern, "/", by the permission constructor.
    @param actions - identifies the HTTP methods to which the permission
    pertains. If the value passed through this parameter is null or the empty
    string, then the permission is constructed with actions corresponding to
    all the possible HTTP methods.
    */
   public WebResourcePermission(String name, String actions)
   {
      super(name == null ? "/" : name);
      if( name == null )
         name = "/";
      this.urlSpec = new URLPatternSpec(name);
      parseActions(actions);
   }

   /** Creates a new WebResourcePermission with name corresponding to the
    * URLPatternSpec, and actions composed from the array of HTTP methods.
    * 
    * @param urlPatternSpec  - the URLPatternSpec that identifies the app
    * specific web resources to which the permission pertains. All URLPatterns
    * in the URLPatternSpec are relative to the context path of the deployed web
    * application module, and the same URLPattern must not occur more than once
    * in a URLPatternSpec. A null URLPatternSpec is translated to the default
    * URLPattern, "/", by the permission constructor.
    * @param httpMethods  - an array of strings each element of which contains
    * the value of an HTTP method. If the value passed through this parameter is
    * null or is an array with no elements, then the permission is constructed
    * with actions corresponding to all the possible HTTP methods.
    */
   public WebResourcePermission(String urlPatternSpec, String[] httpMethods)
   {
      super(urlPatternSpec);
      this.urlSpec = new URLPatternSpec(urlPatternSpec);
      Object[] methodInfo = canonicalMethods(httpMethods);
      this.httpMethods = (TreeSet) methodInfo[0];
      this.httpMethodsString = (String) methodInfo[1];
   }

   /** Checks two WebResourcePermission objects for equality. WebResourcePermission
    * objects are equivalent if their URLPatternSpec and (canonicalized) actions
    * values are equivalent. The URLPatternSpec of a reference permission is
    * equivalent to that of an argument permission if their first patterns are
    * equivalent, and the patterns of the URLPatternList of the reference
    * permission collectively match exactly the same set of patterns as are
    * matched by the patterns of the URLPatternList of the argument permission.
    * 
    * @param p  - the WebResourcePermission object being tested for equality
    * with this WebResourcePermission.
    * @return true if the argument WebResourcePermission object is equivalent to
    * this WebResourcePermission.
    */
   public boolean equals(Object p)
   {
      //boolean equals = false;
      if( p == null || !(p instanceof WebResourcePermission) )
         return false;
      WebResourcePermission perm = (WebResourcePermission) p;
      
      /**
       * Two permissions p1 and p2 are equivalent if and only if p1.implies(p2)
       * and p2.implies(p1)
       */
      return this.implies(perm) && perm.implies(this);
      /*
      equals = urlSpec.equals(perm.urlSpec);
      if( equals == true )
      {
         String a0 = getActions();
         String a1 = perm.getActions();
         equals = (a0 != null && a0.equals(a1)) || (a0 == a1);
      }
      return equals;*/
   }

   /** Returns a canonical String representation of the actions of this
    * WebResourcePermission. WebResourcePermission actions are canonicalized by
    * sorting the HTTP methods into ascending lexical order. There may be no
    * duplicate HTTP methods in the canonical form, and the canonical form of
    * the set of all HTTP methods is the value null.
    * 
    * @return a String containing the canonicalized actions of this
    * WebResourcePermission (or the null value).
    */
   public String getActions()
   {
      return httpMethodsString;
   }

   /** Returns the hash code value for this WebResourcePermission. The properties
    * of the returned hash code must be as follows:
    * 
    * - During the lifetime of a Java application, the hashCode method must return
    * the same integer value, every time it is called on a WebResourcePermission
    * object. The value returned by hashCode for a particular
    * WebResourcePermission need not remain consistent from one execution of an
    * application to another.
    * - If two WebResourcePermission objects are equal according to the equals
    * method, then calling the hashCode method on each of the two Permission
    * objects must produce the same integer result (within an application). 
    * @return the integer hash code value for this object.
    */
   public int hashCode()
   {
      int hashCode = urlSpec.hash();
      if( httpMethods != null )
         hashCode += httpMethods.hashCode();
      return hashCode;
   }

   /** Determines if the argument Permission is "implied by" this
    * WebResourcePermission. For this to be the case, all of the following must
    * be true:

    * - The argument is an instanceof WebResourcePermission
    * - The first URLPattern in the name of the argument permission is matched
    * by the first URLPattern in the name of this permission.
    * - The first URLPattern in the name of the argument permission is NOT
    * matched by any URLPattern in the URLPatternList of the URLPatternSpec
    * of this permission.
    * - If the first URLPattern in the name of the argument permission matches
    * the first URLPattern in the URLPatternSpec of this permission, then every
    * URLPattern in the URLPatternList of the URLPatternSpec of this permission
    * is matched by a URLPattern in the URLPatternList of the argument permission.
    * - The HTTP methods in the actions of the argument permission are a subset
    * of the HTTP methods in the actions of this permission.
    * 
    * URLPattern matching is performed using the Servlet matching rules where
    * two URL patterns match if they are related as follows:
    * - their pattern values are String equivalent, or
    * - this pattern is the path-prefix pattern "/*", or
    * - this pattern is a path-prefix pattern (that is, it starts with "/" and
    * ends with "/*") and the argument pattern starts with the substring of this
    * pattern, minus its last 2 characters, and the next character of the argument
    * pattern, if there is one, is "/", or
    * - this pattern is an extension pattern (that is, it starts with "*.") and
    * the argument pattern ends with this pattern, or
    * - the reference pattern is the special default pattern, "/", which matches
    * all argument patterns.
    * 
    * All of the comparisons described above are case sensitive. 
    * 
    * @param p
    * @return
    */
   public boolean implies(Permission p)
   {
      if( p == null || !(p instanceof WebResourcePermission) )
         return false;
      WebResourcePermission perm = (WebResourcePermission) p;
      // Check the URL patterns
      boolean implies = urlSpec.implies(perm.urlSpec);
      if( implies == true )
      {
         if(httpExceptionList != null)
            implies = matchExceptionList(httpExceptionList, perm.httpExceptionList); 
         //Check the http methods
         if( httpMethods != null && perm.httpMethods != null)
               implies = httpMethods.containsAll(perm.httpMethods); 
      }

      return implies;
   }

   /** Build a permission name from the substring of the
    * HttpServletRequest.getRequestURI()) that begins after the contextPath
    * (HttpServletRequest.getContextPath()). When the substring operation yields
    * the string "/", the permission is constructed with the empty string as
    * its name. 
    * @param request - the servlet request
    * @return the resource permission name
    */ 
   static String requestURI(HttpServletRequest request)
   {
      String uri = request.getRequestURI();
      if( uri != null )
      {
         String contextPath = request.getContextPath();
         int length = contextPath == null ? 0 : contextPath.length();
         if( length > 0 )
         {
            uri = uri.substring(length);
         }
         if( uri.equals("/") )
         {
            uri = "";
         }
      }
      else
      {
         uri = "";
      }
      return uri;
   }

   static Object[] canonicalMethods(String[] methods)
   {
      TreeSet actions = new TreeSet();
      int length = methods != null ? methods.length : 0;
      for(int n = 0; n < length; n++)
      {
         actions.add(methods[n]);
      }
      return canonicalMethods(actions);
   }

   /** Parse the comma seperated http methods into a  
    * @param methods
    * @return
    */
   static Object[] canonicalMethods(String methods)
   {
      if( methods == null || methods.length() == 0 )
         return new Object[]{ALL_HTTP_METHODS, null};

      StringTokenizer tokenizer = new StringTokenizer(methods, ",");
      TreeSet actions = new TreeSet();
      while( tokenizer.hasMoreTokens() )
      {
         String action = tokenizer.nextToken();
         actions.add(action);
      }
      return canonicalMethods(actions);
   }

   static Object[] canonicalMethods(TreeSet actions)
   {
      Object[] info = {null, null};
      if( actions.equals(ALL_HTTP_METHODS) || actions.size() == 0 )
         return info;

      info[0] = actions;
      Iterator iter = actions.iterator();
      StringBuffer tmp = new StringBuffer();
      while( iter.hasNext() )
      {
         tmp.append(iter.next());
         tmp.append(',');
      }
      if( tmp.length() > 0 )
         tmp.setLength(tmp.length() - 1);
      info[1] = tmp.toString();
      return info;
   }

   // Private -------------------------------------------------------
   private void parseActions(String actions)
   {
      boolean exclusionListNeeded = actions != null && actions.startsWith("!");
      if(exclusionListNeeded) 
         actions = actions.substring(1); 
      
      Object[] methodInfo = canonicalMethods(actions);
      if(exclusionListNeeded)
      {
         this.httpExceptionList = (TreeSet) methodInfo[0];
         this.httpExceptionString = (String) methodInfo[1]; 
      }
      else
      { 
         this.httpMethods = (TreeSet) methodInfo[0];
         this.httpMethodsString = (String) methodInfo[1];
      }
   }

   private void readObject(ObjectInputStream ois)
      throws ClassNotFoundException, IOException
   {
      ObjectInputStream.GetField fields = ois.readFields();
      String actions = (String) fields.get("actions", null);
      parseActions(actions);
   }

   private void writeObject(ObjectOutputStream oos)
      throws IOException
   {
      ObjectOutputStream.PutField fields =  oos.putFields();
      fields.put("actions", this.getActions());
      oos.writeFields();
   } 
   
   static boolean matchExceptionList(TreeSet<String> myExceptionList,
          TreeSet<String> matchingExceptionList)
   { 
      boolean bothnull = (myExceptionList == null && matchingExceptionList == null);
      boolean onenull = (myExceptionList == null && matchingExceptionList != null)
               || (myExceptionList != null && matchingExceptionList == null);
      
      if(bothnull)
         return true;
      if(onenull)
         return false;  
      
      for(String httpMethod: matchingExceptionList)
      {
         if(myExceptionList.contains(httpMethod))
            return false;
      }
      return true;
   }
}
