/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;

import org.restlet.data.Form;
import org.restlet.data.Metadata;
import org.restlet.data.Parameter;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.ext.jaxrs.internal.core.UnmodifiableMultivaluedMap;
import org.restlet.ext.jaxrs.internal.exceptions.IllegalPathException;
import org.restlet.ext.jaxrs.internal.exceptions.IllegalPathOnClassException;
import org.restlet.ext.jaxrs.internal.exceptions.IllegalPathOnMethodException;
import org.restlet.ext.jaxrs.internal.exceptions.InjectException;
import org.restlet.ext.jaxrs.internal.exceptions.JaxRsRuntimeException;
import org.restlet.ext.jaxrs.internal.exceptions.MethodInvokeException;
import org.restlet.ext.jaxrs.internal.exceptions.MissingAnnotationException;
import org.restlet.resource.Representation;
import org.restlet.util.DateUtils;
import org.restlet.util.Engine;
import org.restlet.util.Series;

/**
 * This class contains utility methods.
 * 
 * @author Stephan Koops
 */
public class Util {

    /**
     * This comparator sorts the concrete MediaTypes to the beginning and the
     * unconcrete to the end. The last is '*<!---->/*'
     */
    public static final Comparator<org.restlet.data.MediaType> MEDIA_TYPE_COMP = new Comparator<org.restlet.data.MediaType>() {
        public int compare(org.restlet.data.MediaType mediaType1,
                org.restlet.data.MediaType mediaType2) {
            if (mediaType1 == null)
                return mediaType2 == null ? 0 : 1;
            if (mediaType2 == null)
                return -1;
            if (mediaType1.equals(mediaType2, false))
                return 0;
            int specNess1 = specificness(mediaType1);
            int specNess2 = specificness(mediaType2);
            int rt = specNess1 - specNess2;
            if (rt != 0)
                return rt;
            // LATER optimizing possible here: do not use toString()
            return mediaType1.toString().compareToIgnoreCase(
                    mediaType2.toString());
        }
    };

    /**
     * The name of the header {@link MultivaluedMap}&lt;String, String&gt; in
     * the attribute map.
     * 
     */
    public static final String ORG_RESTLET_EXT_JAXRS_HTTP_HEADERS = "org.restlet.ext.jaxrs.http.headers";

    /**
     * The name of the header {@link Form} in the attribute map.
     * 
     * @see #getHttpHeaders(Request)
     * @see #getHttpHeaders(Response)
     */
    public static final String ORG_RESTLET_HTTP_HEADERS = "org.restlet.http.headers";

    /**
     * appends the given String to the StringBuilder. If convertBraces is true,
     * all "{" and "}" are converted to "%7B" and "%7D"
     * 
     * @param stb
     *                the Appendable to append on
     * @param string
     *                the CharSequence to append
     * @param convertBraces
     *                if true, all braces are converted, if false then not.
     * @throws IOException
     *                 If the Appendable have a problem
     */
    public static void append(Appendable stb, CharSequence string,
            boolean convertBraces) throws IOException {
        if (!convertBraces) {
            stb.append(string);
            return;
        }
        int l = string.length();
        for (int i = 0; i < l; i++) {
            char c = string.charAt(i);
            if (c == '{')
                stb.append("%7B");
            else if (c == '}')
                stb.append("%7D");
            else
                stb.append(c);
        }
    }

    /**
     * appends the array elements to the {@link StringBuilder}, separated by ", ".
     * 
     * @param stb
     *                The {@link StringBuilder} to append the array elements.
     * @param array
     *                The array to append to the {@link StringBuilder}.
     */
    public static void append(StringBuilder stb, Object[] array) {
        if (array == null || array.length == 0)
            return;
        stb.append(array[0]);
        for (int i = 1; i < array.length; i++) {
            stb.append(", ");
            stb.append(array[i]);
        }
    }

    /**
     * Checks, if the class is concrete.
     * 
     * @param jaxRsClass
     *                JAX-RS root resource class or JAX-RS provider.
     * @param typeName
     *                for the exception message "root resource class" or
     *                "provider"
     * @throws IllegalArgumentException
     *                 if the class is not concrete.
     */
    public static void checkClassConcrete(Class<?> jaxRsClass, String typeName)
            throws IllegalArgumentException {
        int modifiers = jaxRsClass.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
            throw new IllegalArgumentException("The " + typeName + " "
                    + jaxRsClass.getName() + " is not concrete");
        }
    }

    /**
     * Copies headers into a response.
     * 
     * @param jaxRsHeaders
     *                Headers of an JAX-RS-Response.
     * @param restletResponse
     *                Restlet Response to copy the headers in.
     * @param logger
     *                The logger to use
     * @see javax.ws.rs.core.Response#getMetadata()
     */
    public static void copyResponseHeaders(
            final MultivaluedMap<String, Object> jaxRsHeaders,
            Response restletResponse, Logger logger) {
        Collection<Parameter> headers = new ArrayList<Parameter>();
        for (Map.Entry<String, List<Object>> m : jaxRsHeaders.entrySet()) {
            String headerName = m.getKey();
            for (Object headerValue : m.getValue()) {
                String hValue;
                if (headerValue == null)
                    hValue = null;
                else if (headerValue instanceof Date)
                    hValue = formatDate((Date) headerValue, false);
                // TODO temporarily constant not as cookie.
                else
                    hValue = headerValue.toString();
                headers.add(new Parameter(headerName, hValue));
            }
        }
        if (restletResponse.getEntity() == null) {
            restletResponse.setEntity(Representation.createEmpty());
        }
        Engine.getInstance().copyResponseHeaders(headers, restletResponse,
                logger);
    }

    /**
     * Copies the headers of the given {@link Response} into the given
     * {@link Series}.
     * 
     * @param restletResponse
     *                The response to update. Should contain a
     *                {@link Representation} to copy the representation headers
     *                from it.
     * @param logger
     *                The logger to use.
     * @return The copied headers.
     */
    public static Series<Parameter> copyResponseHeaders(
            Response restletResponse, Logger logger) {
        Series<Parameter> headers = new Form();
        Engine engine = Engine.getInstance();
        engine.copyResponseHeaders(restletResponse, headers, logger);
        return headers;
    }

    /**
     * Copiees the InputStream to the OutputStream.
     * 
     * @param in
     * @param out
     * @throws IOException
     */
    public static void copyStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[512];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) >= 0) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Copies the InputStream to a StringBuilder.
     * 
     * @param in
     * @return a StringBuilder with the content of the given InputStream
     * @throws IOException
     */
    public static StringBuilder copyToStringBuilder(InputStream in)
            throws IOException {
        StringBuilder stb = new StringBuilder();
        int ch;
        while ((ch = in.read()) >= 0)
            stb.append((char) ch);
        return stb;
    }

    /**
     * Creates an modifiable Collection with the given Objects in it, and no
     * other objects. nulls will be ignored.
     * 
     * @param objects
     * @param <A>
     * @return Returns the created list with the given objects in it.
     */
    public static <A> Collection<A> createColl(A... objects) {
        return createList(objects);
    }

    /**
     * Creates an modifiable List with the given Object in it, and no other
     * objects. If the given object is null, than an empty List will returned
     * 
     * @param objects
     * @param <A>
     * @return Returns the created list with the given object in it or an empty
     *         list, if the given object is null.
     */
    public static <A> List<A> createList(A... objects) {
        List<A> list = new ArrayList<A>();
        int l = objects.length;
        for (int i = 0; i < l; i++) {
            A o = objects[i];
            if (o != null)
                list.add(o);
        }
        return list;
    }

    /**
     * Creates a map with the given keys and values.
     * 
     * @param keysAndValues
     *                first element is key1, second element value1, third
     *                element key2, forth element value2 and so on.
     * @return
     */
    public static Map<String, String> createMap(String... keysAndValues) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    /**
     * Creates an modifiable Set with the given Object in it, and no other
     * objects. If the given object is null, than an empty Set will returned.
     * 
     * @param <A>
     * @param objects
     * @return the created Set
     */
    public static <A> Set<A> createSet(A... objects) {
        Set<A> set = new HashSet<A>();
        int l = objects.length;
        for (int i = 0; i < l; i++) {
            A o = objects[i];
            if (o != null)
                set.add(o);
        }
        return set;
    }

    /**
     * Check if the given objects are equal. Can deal with null references. if
     * both elements are null, than the result is true.
     * 
     * @param object1
     * @param object2
     * @return
     */
    public static boolean equals(Object object1, Object object2) {
        if (object1 == null)
            return object2 == null;
        return object1.equals(object2);
    }

    /**
     * Converts the given Date into a String. Copied from
     * {@link com.noelios.restlet.HttpCall}.
     * 
     * @param date
     *                Date to format
     * @param cookie
     *                if true, using RFC 1036 format, otherwise RFC 1123 format.
     * @return
     */
    public static String formatDate(Date date, boolean cookie) {
        if (cookie) {
            return DateUtils.format(date, DateUtils.FORMAT_RFC_1036.get(0));
        } else {
            return DateUtils.format(date, DateUtils.FORMAT_RFC_1123.get(0));
        }
    }

    /**
     * Returns the first element of the given collection. Throws an exception if
     * the collection is empty.
     * 
     * @param coll
     * @param <A>
     * @return Returns the first Element of the collection
     * @throws NoSuchElementException
     *                 If the collection is empty.
     */
    public static <A> A getFirstElement(Collection<A> coll)
            throws NoSuchElementException {
        if (coll.isEmpty())
            throw new NoSuchElementException(
                    "The Collection is empty; you can't get the first element of it.");
        if (coll instanceof LinkedList)
            return ((LinkedList<A>) coll).getFirst();
        if (coll instanceof List)
            return ((List<A>) coll).get(0);
        return coll.iterator().next();
    }

    /**
     * Returns the first element of the given {@link Iterable}. Throws an
     * exception if the {@link Iterable} is empty.
     * 
     * @param coll
     * @param <A>
     * @return Returns the first Element of the collection
     * @throws NoSuchElementException
     *                 If the collection is empty
     */
    public static <A> A getFirstElement(Iterable<A> coll)
            throws NoSuchElementException {
        if (coll instanceof LinkedList)
            return ((LinkedList<A>) coll).getFirst();
        if (coll instanceof List)
            return ((List<A>) coll).get(0);
        return coll.iterator().next();
    }

    /**
     * Returns the first element of the {@link List}. Throws an exception if
     * the list is empty.
     * 
     * @param list
     * @param <A>
     * @return Returns the first Element of the collection
     * @throws IndexOutOfBoundsException
     *                 If the list is empty
     */
    public static <A> A getFirstElement(List<A> list)
            throws IndexOutOfBoundsException {
        if (list.isEmpty())
            throw new IndexOutOfBoundsException(
                    "The Collection is empty; you can't get the first element of it.");
        if (list instanceof LinkedList)
            return ((LinkedList<A>) list).getFirst();
        return list.get(0);
    }

    /**
     * Returns the first element of the given {@link Iterable}. Returns null,
     * if the {@link Iterable} is empty.
     * 
     * @param coll
     * @param <A>
     * @return the first element of the collection, or null if the iterable is
     *         empty.
     */
    public static <A> A getFirstElementOrNull(Iterable<A> coll) {
        if (coll instanceof LinkedList) {
            LinkedList<A> linkedList = ((LinkedList<A>) coll);
            if (linkedList.isEmpty())
                return null;
            return linkedList.getFirst();
        }
        if (coll instanceof List) {
            List<A> list = ((List<A>) coll);
            if (list.isEmpty())
                return null;
            return list.get(0);
        }
        if (coll instanceof Collection) {
            if (((Collection<A>) coll).isEmpty())
                return null;
        }
        return coll.iterator().next();
    }

    /**
     * Returns the first entry of the given {@link Map}. Throws an exception if
     * the Map is empty.
     * 
     * @param map
     * @param <K>
     * @param <V>
     * @return the first entry of the given {@link Map}.
     * @throws NoSuchElementException
     *                 If the map is empty.
     */
    public static <K, V> Map.Entry<K, V> getFirstEntry(Map<K, V> map)
            throws NoSuchElementException {
        return map.entrySet().iterator().next();
    }

    /**
     * Returns the key of the first entry of the given {@link Map}. Throws an
     * exception if the Map is empty.
     * 
     * @param map
     * @param <K>
     * @param <V>
     * @return the key of the first entry of the given {@link Map}.
     * @throws NoSuchElementException
     *                 If the map is empty.
     */
    public static <K, V> K getFirstKey(Map<K, V> map)
            throws NoSuchElementException {
        return map.keySet().iterator().next();
    }

    /**
     * Returns the value of the first entry of the given {@link Map}. Throws an
     * exception if the Map is empty.
     * 
     * @param map
     * @param <K>
     * @param <V>
     * @return the value of the first entry of the given {@link Map}.
     * @throws NoSuchElementException
     *                 If the map is empty.
     */
    public static <K, V> V getFirstValue(Map<K, V> map)
            throws NoSuchElementException {
        return map.values().iterator().next();
    }

    /**
     * Returns the HTTP headers of the Restlet {@link Request} as {@link Form}.
     * 
     * @param request
     * @return Returns the HTTP headers of the Request.
     */
    public static Form getHttpHeaders(Request request) {
        Form headers = (Form) request.getAttributes().get(
                ORG_RESTLET_HTTP_HEADERS);
        if (headers == null) {
            headers = new Form();
            request.getAttributes().put(ORG_RESTLET_HTTP_HEADERS, headers);
        }
        return headers;
    }

    /**
     * Returns the HTTP headers of the Restlet {@link Response} as {@link Form}.
     * 
     * @param response
     * @return Returns the HTTP headers of the Response.
     */
    public static Form getHttpHeaders(Response response) {
        Form headers = (Form) response.getAttributes().get(
                ORG_RESTLET_HTTP_HEADERS);
        if (headers == null) {
            headers = new Form();
            response.getAttributes().put(ORG_RESTLET_HTTP_HEADERS, headers);
        }
        return headers;
    }

    /**
     * Returns the request headers as {@link MultivaluedMap}.
     * 
     * @param request
     * @return
     */
    public static MultivaluedMap<String, String> getJaxRsHttpHeaders(
            Request request) {
        Map<String, Object> attrsOfRequ = request.getAttributes();
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> headers = (MultivaluedMap) attrsOfRequ
                .get(ORG_RESTLET_EXT_JAXRS_HTTP_HEADERS);
        if (headers == null) {
            headers = UnmodifiableMultivaluedMap.getFromForm(
                    getHttpHeaders(request), false);
            attrsOfRequ.put(ORG_RESTLET_EXT_JAXRS_HTTP_HEADERS, headers);
        }
        return headers;
    }

    /**
     * Returns the last element of the given {@link Iterable}. Throws an
     * exception if the given iterable is empty.
     * 
     * @param iterable
     * @param <A>
     * @return Returns the last element of the {@link Iterable}
     * @throws IndexOutOfBoundsException
     *                 If the {@link Iterable} is a {@link List} and its is
     *                 empty.
     * @throws NoSuchElementException
     *                 If the {@link Iterable} is empty and the {@link Iterable}
     *                 is not a {@link List}.
     */
    public static <A> A getLastElement(Iterable<A> iterable)
            throws IndexOutOfBoundsException, NoSuchElementException {
        if (iterable instanceof LinkedList)
            return ((LinkedList<A>) iterable).getLast();
        if (iterable instanceof List) {
            List<A> list = ((List<A>) iterable);
            return list.get(list.size() - 1);
        }
        return getLastElement(iterable.iterator());
    }

    /**
     * Returns the last element of the given {@link Iterator}. Throws an
     * exception if the given iterator is empty.
     * 
     * @param iter
     * @param <A>
     * @return Returns the last element of the {@link Iterator}.
     * @throws NoSuchElementException
     *                 If the {@link Iterator} is empty.
     */
    public static <A> A getLastElement(Iterator<A> iter)
            throws NoSuchElementException {
        A e = iter.next();
        while (iter.hasNext())
            e = iter.next();
        return e;
    }

    /**
     * Returns the last element of the given {@link List}. Throws an exception
     * if the given list is empty.
     * 
     * @param list
     * @param <A>
     * @return Returns the last element of the list
     * @throws IndexOutOfBoundsException
     *                 If the list is empty
     */
    public static <A> A getLastElement(List<A> list)
            throws IndexOutOfBoundsException {
        if (list instanceof LinkedList)
            return ((LinkedList<A>) list).getLast();
        return list.get(list.size() - 1);
    }

    /**
     * Returns the last element of the given {@link Iterable}, or null, if the
     * iterable is empty. Returns null, if the iterable is empty.
     * 
     * @param iterable
     * @param <A>
     * @return Returns the last Element of the {@link Iterable}, or null if it
     *         is empty.
     */
    public static <A> A getLastElementOrNull(Iterable<A> iterable) {
        if (iterable instanceof LinkedList) {
            LinkedList<A> linkedList = ((LinkedList<A>) iterable);
            if (linkedList.isEmpty())
                return null;
            return linkedList.getLast();
        }
        if (iterable instanceof List) {
            List<A> list = ((List<A>) iterable);
            if (list.isEmpty())
                return null;
            return list.get(list.size() - 1);
        }
        if (iterable instanceof Collection) {
            if (((Collection<A>) iterable).isEmpty())
                return null;
        }
        return getLastElementOrNull(iterable.iterator());
    }

    /**
     * Returns the last element of the given {@link Iterator}, or null, if the
     * iterator is empty. Returns null, if the iterator is empty.
     * 
     * @param iter
     * @param <A>
     * @return Returns the last Element of the {@link Iterator}.
     */
    public static <A> A getLastElementOrNull(Iterator<A> iter) {
        A e = null;
        while (iter.hasNext())
            e = iter.next();
        return e;
    }

    /**
     * Returns all public {@link Method}s of the class with the given name
     * (case-sensitive)
     * 
     * @param clazz
     *                The {@link Class} to search the {@link Method}s.
     * @param methodName
     *                The name of the {@link Method} to search.
     * @return Returns a {@link Collection} all of {@link Method}s with the
     *         given name. Never returns null. If no methods are found an empty
     *         Collection will be returned. The method {@link Iterator#remove()}
     *         of this collection is supported.
     * @throws IllegalArgumentException
     *                 if the clazz or the method name is null.
     */
    public static Collection<Method> getMethodsByName(Class<?> clazz,
            String methodName) throws IllegalArgumentException {
        if (clazz == null)
            throw new IllegalArgumentException("The class must not be null");
        if (methodName == null)
            throw new IllegalArgumentException(
                    "The method name must not be null");
        Collection<Method> methods = new ArrayList<Method>(2);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName))
                methods.add(method);
        }
        return methods;
    }

    /**
     * Returns the only element of the list, or null, if the List is null or
     * empty.
     * 
     * @param <A>
     * @param list
     *                a List with at most one element
     * @return The element of the List, or null, if there is no element.
     * @throws IllegalArgumentException
     *                 if the list contains more than one element.
     */
    public static <A> A getOnlyElement(Collection<A> list)
            throws IllegalArgumentException {
        if (list == null)
            return null;
        if (list.isEmpty())
            return null;
        if (list.size() > 1)
            throw new IllegalArgumentException(
                    "The list must have exactly one element");
        if (list instanceof List)
            return ((List<A>) list).get(0);
        return list.iterator().next();
    }

    /**
     * Returns the Name of the only element of the list of the given Metadata.
     * Returns null, if the list is empty or null.
     * 
     * @param metadatas
     * @return the name of the Metadata
     * @see #getOnlyElement(List)
     */
    public static String getOnlyMetadataName(List<? extends Metadata> metadatas) {
        Metadata metadata = getOnlyElement(metadatas);
        if (metadata == null)
            return null;
        return metadata.getName();
    }

    /**
     * Returns the &#64;{@link Path} annotation of the given root resource
     * class.
     * 
     * @param jaxRsClass
     *                the root resource class.
     * @return the &#64;{@link Path} annotation of the given root resource
     *         class.
     * @throws MissingAnnotationException
     *                 if the path annotation is missing
     * @throws IllegalArgumentException
     *                 if the jaxRsClass is null.
     */
    public static Path getPathAnnotation(Class<?> jaxRsClass)
            throws MissingAnnotationException, IllegalArgumentException {
        if (jaxRsClass == null)
            throw new IllegalArgumentException(
                    "The jaxRsClass must not be null");
        Path path = jaxRsClass.getAnnotation(Path.class);
        if (path == null)
            throw new MissingAnnotationException(
                    "The root resource class does not have a @Path annotation");
        return path;
    }

    /**
     * Returns the &#64;{@link Path} annotation of the given sub resource
     * locator. Throws an exception if no &#64;{@link Path} annotation is
     * available.
     * 
     * @param method
     *                the java method to get the &#64;Path from
     * @return the &#64;Path annotation.
     * @throws IllegalArgumentException
     *                 if null was given.
     * @throws MissingAnnotationException
     *                 if the annotation is not present.
     */
    public static Path getPathAnnotation(Method method)
            throws IllegalArgumentException, MissingAnnotationException {
        if (method == null)
            throw new IllegalArgumentException(
                    "The root resource class must not be null");
        Path path = method.getAnnotation(Path.class);
        if (path == null)
            throw new MissingAnnotationException("The method "
                    + method.getName() + " does not have an annotation @Path");
        return path;
    }

    /**
     * Returns the &#64;{@link Path} annotation of the given sub resource
     * locator. Returns null if no &#64;{@link Path} annotation is available.
     * 
     * @param method
     *                the java method to get the &#64;Path from
     * @return the &#64;Path annotation or null, if not present.
     * @throws IllegalArgumentException
     *                 if the method is null.
     */
    public static Path getPathAnnotationOrNull(Method method)
            throws IllegalArgumentException {
        if (method == null)
            throw new IllegalArgumentException(
                    "The root resource class must not be null");
        return method.getAnnotation(Path.class);
    }

    /**
     * Returns the perhaps decoded template of the path annotation.
     * 
     * @param resource
     * @return Returns the path template as String. Never returns null.
     * @throws IllegalPathOnClassException
     * @throws MissingAnnotationException
     * @throws IllegalArgumentException
     */
    public static String getPathTemplate(Class<?> resource)
            throws IllegalPathOnClassException, MissingAnnotationException,
            IllegalArgumentException {
        try {
            return getPathTemplate(Util.getPathAnnotation(resource));
        } catch (IllegalPathException e) {
            throw new IllegalPathOnClassException(e);
        }
    }

    /**
     * Returns the path template of the given sub resource locator or sub
     * resource method. It is encoded (if necessary) and valid.
     * 
     * @param method
     *                the java method
     * @return the path template
     * @throws IllegalPathOnMethodException
     * @throws IllegalArgumentException
     * @throws MissingAnnotationException
     */
    public static String getPathTemplate(Method method)
            throws IllegalArgumentException, IllegalPathOnMethodException,
            MissingAnnotationException {
        Path path = getPathAnnotation(method);
        try {
            return getPathTemplate(path);
        } catch (IllegalPathException e) {
            throw new IllegalPathOnMethodException(e);
        }
    }

    /**
     * Returns the path from the annotation. It will be encoded if necessary. If
     * it should not be encoded, this method checks, if all characters are
     * valid.
     * 
     * @param path
     *                The {@link Path} annotation. Must not be null.
     * @return the encoded path template
     * @throws IllegalPathException
     * @see Path#encode()
     */
    public static String getPathTemplate(Path path) throws IllegalPathException {
        // LATER EncodeOrCheck.path(CharSequence)
        String pathTemplate = path.value();
        if (pathTemplate.contains(";"))
            throw new IllegalPathException(path,
                    "A path must not contain a semicolon");
        if (path.encode()) {
            pathTemplate = EncodeOrCheck.encodeNotBraces(pathTemplate, false)
                    .toString();
        } else {
            try {
                EncodeOrCheck.checkForInvalidUriChars(pathTemplate, -1,
                        "path template");
            } catch (IllegalArgumentException iae) {
                throw new IllegalPathException(path, iae);
            }
        }
        return pathTemplate;
    }

    /**
     * Inject the given toInject into the given field in the given resource (or
     * whatever)
     * 
     * @param resource
     *                the concrete Object to inject the other object in. If the
     *                field is static, thsi object may be null.
     * @param field
     *                the field to inject the third parameter in.
     * @param toInject
     *                the object to inject in the first parameter object.
     * @throws InjectException
     *                 if the injection was not possible. See
     *                 {@link InjectException#getCause()} for the reason.
     */
    public static void inject(final Object resource, final Field field,
            final Object toInject) throws InjectException {
        try {
            IllegalAccessException iae = AccessController
                    .doPrivileged(new PrivilegedAction<IllegalAccessException>() {
                        public IllegalAccessException run() {
                            try {
                                field.set(resource, toInject);
                                return null;
                            } catch (IllegalAccessException e) {
                                return e;
                            }
                        }
                    });
            if (iae != null)
                throw new InjectException("Could not inject the "
                        + toInject.getClass() + " into field " + field
                        + " of object " + resource, iae);
        } catch (RuntimeException e) {
            throw new InjectException("Could not inject the "
                    + toInject.getClass() + " into field " + field
                    + " of object " + resource, e);
        }
    }

    /**
     * Invokes the given method without parameters. This constraint is not
     * checked; but the method could also be called, if access is normally not
     * allowed.<br>
     * If no javaMethod is given, nothing happens.
     * 
     * @param object
     * @param javaMethod
     * @throws MethodInvokeException
     * @throws InvocationTargetException
     * @see #inject(Object, Field, Object)
     * @see #findPostConstructMethod(Class)
     * @see #findPreDestroyMethod(Class)
     */
    public static void invokeNoneArgMethod(final Object object,
            final Method javaMethod) throws MethodInvokeException,
            InvocationTargetException {
        if (javaMethod == null)
            return;
        javaMethod.setAccessible(true);
        try {
            AccessController
                    .doPrivileged(new PrivilegedExceptionAction<Object>() {
                        public Object run() throws Exception {
                            javaMethod.invoke(object);
                            return null;
                        }
                    });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalAccessException)
                throw new MethodInvokeException(
                        "Not allowed to invoke post construct method "
                                + javaMethod, cause);
            if (cause instanceof InvocationTargetException)
                throw (InvocationTargetException) cause;
            if (cause instanceof ExceptionInInitializerError)
                throw new MethodInvokeException(
                        "Could not invoke post construct method " + javaMethod,
                        cause);
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            throw new JaxRsRuntimeException(
                    "Error while invoking post construct method " + javaMethod,
                    cause);
        }
    }

    /**
     * Checks, if the list is empty or null.
     * 
     * @param list
     * @return true, if the list is empty or null, or false, if the list
     *         contains elements.
     * @see #isEmpty(Object[])
     */
    public static boolean isEmpty(List<?> list) {
        return (list == null || list.isEmpty());
    }

    /**
     * Tests, if the given array is empty or null. Will not throw a
     * NullPointerException.
     * 
     * @param array
     * @return Returns true, if the given array ist null or has zero elements,
     *         otherwise false.
     * @see #isEmpty(List)
     */
    public static boolean isEmpty(Object[] array) {
        if (array == null || array.length == 0)
            return true;
        return false;
    }

    /**
     * Tests, if the given String is null, empty or "/". Will not throw a
     * NullPointerException.
     * 
     * @param string
     * @return Returns true, if the given string ist null, empty or equals "/",
     *         otherwise false.
     */
    public static boolean isEmptyOrSlash(String string) {
        return string == null || string.length() == 0 || string.equals("/");
    }

    /**
     * Checks, if the list contains elements.
     * 
     * @param list
     * @return true, if the list contains elements, or false, if the list is
     *         empty or null.
     */
    public static boolean isNotEmpty(List<?> list) {
        return (list != null && !list.isEmpty());
    }

    /**
     * Returns a new {@link List}, which contains all
     * {@link org.restlet.data.MediaType}s of the given List, sorted by it's
     * concreteness, the concrete {@link org.restlet.data.MediaType} at the
     * beginning.
     * 
     * @param mediaTypes
     * @return
     * @see Util#specificness(org.restlet.data.MediaType)
     */
    public static List<org.restlet.data.MediaType> sortByConcreteness(
            Collection<org.restlet.data.MediaType> mediaTypes) {
        List<org.restlet.data.MediaType> newList = new ArrayList<org.restlet.data.MediaType>(
                mediaTypes.size());
        for (org.restlet.data.MediaType mediaType : mediaTypes)
            if (specificness(mediaType) > 0)
                newList.add(mediaType);
        for (org.restlet.data.MediaType mediaType : mediaTypes)
            if (specificness(mediaType) == 0)
                newList.add(mediaType);
        for (org.restlet.data.MediaType mediaType : mediaTypes)
            if (specificness(mediaType) < 0)
                newList.add(mediaType);
        return newList;
    }

    /**
     * Returns the specificness of the given {@link org.restlet.data.MediaType}:
     * <ul>
     * <li>1 for any concrete type (contains no star)</li>
     * <li>0 for the types (anything/*)</li>
     * <li>-1 for '*<!---->/*</li>
     * </ul>
     * 
     * @param mediaType
     * @return 1, 0 or -1
     * @see #isConcrete(org.restlet.data.MediaType)
     * @see #sortByConcreteness(Collection)
     */
    public static int specificness(org.restlet.data.MediaType mediaType) {
        if (mediaType.equals(org.restlet.data.MediaType.ALL, true))
            return -1;
        if (mediaType.getSubType().equals("*"))
            return 0;
        return 1;
    }

    /**
     * Creates an Array of the given type.
     * 
     * @param coll
     * @param arrayType
     * @return
     * @throws NegativeArraySizeException
     */
    public static Object toArray(Collection<?> coll, Class<?> arrayType) {
        int collSize = coll.size();
        Object[] array = (Object[]) Array.newInstance(arrayType, collSize);
        return coll.toArray(array);
    }
}