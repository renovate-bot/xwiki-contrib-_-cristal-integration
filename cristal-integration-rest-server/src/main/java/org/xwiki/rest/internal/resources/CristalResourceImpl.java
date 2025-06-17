/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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
package org.xwiki.rest.internal.resources;

import java.text.SimpleDateFormat;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.resources.CristalResource;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.ExternalServletURLFactory;
import com.xpn.xwiki.web.XWikiURLFactory;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

/**
 * Default implementation of {@link CristalResource}.
 *
 * @version $Id$
 */
@Component
@Named("org.xwiki.rest.internal.resources.CristalResourceImpl")
public class CristalResourceImpl extends XWikiResource implements CristalResource
{
    @Inject
    private ContextualAuthorizationManager contextualAuthorizationManager;

    @Override
    public Response getPage(String wikiName, String spaceName, String pageName, String format,
        String revision) throws XWikiRestException
    {
        try {
            List<String> spaces = parseSpaceSegments(spaceName);

            DocumentReference documentReference = new DocumentReference(wikiName, spaces, pageName);
            try {
                this.contextualAuthorizationManager.checkAccess(Right.VIEW, documentReference);
            } catch (AccessDeniedException e) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

            DocumentInfo documentInfo = getDocumentInfo(wikiName, spaceName, pageName, null, revision, true, false);
            Document doc = documentInfo.getDocument();

            if (format != null && format.equals("jsonld")) {
                JsonObjectBuilder builder = Json.createObjectBuilder()
                    .add("@context", "https://schema.org")
                    .add("@type", "CreativeWork");
                this.mapJsonStandardFields(builder, doc);
                builder.add("canEdit", this.contextualAuthorizationManager.hasAccess(Right.EDIT, documentReference));

                XWikiContext context = getXWikiContext();
                XWikiURLFactory externalUrlFactory = new ExternalServletURLFactory(context);
                context.setURLFactory(externalUrlFactory);
                XWikiDocument xdoc = context.getWiki().getDocument(doc.getDocumentReference(), context);
                context.setDoc(xdoc);
                builder.add("html", xdoc.getRenderedContent(context));

                return Response.ok().entity(builder.build().toString()).type(MediaType.APPLICATION_JSON).build();
            } else {
                return Response.ok().entity(doc).build();
            }
        } catch (XWikiException e) {
            throw new XWikiRestException(e);
        }
    }

    private void mapJsonStandardFields(JsonObjectBuilder jsonBuilder, Document doc) throws XWikiException
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        jsonBuilder
            .add("identifier", doc.getFullName())
            .add("url", doc.getExternalURL())
            .add("name",
                doc.getName().equals("WebHome")
                    ? doc.getDocumentReference().getParent().getName()
                    : doc.getName())
            .add("headline", doc.getPlainTitle())
            .add("headlineRaw", doc.getTitle())
            .add("language", doc.getRealLanguage())
            .add("dateCreated", sdf.format(doc.getCreationDate()))
            .add("dateModified", sdf.format(doc.getDate()))
            .add("creator", doc.getCreator())
            .add("editor", doc.getAuthor())
            .add("version", doc.getVersion())
            .add("encodingFormat", doc.getSyntaxId())
            .add("text", doc.getContent());
    }
}
