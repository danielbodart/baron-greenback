package com.googlecode.barongreenback.crawler;

import com.googlecode.barongreenback.jobs.JobsResource;
import com.googlecode.barongreenback.persistence.StringPrintStream;
import com.googlecode.barongreenback.queues.QueuesResource;
import com.googlecode.barongreenback.shared.Forms;
import com.googlecode.barongreenback.shared.ModelRepository;
import com.googlecode.barongreenback.shared.RecordDefinition;
import com.googlecode.funclate.Model;
import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Pair;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Strings;
import com.googlecode.totallylazy.Uri;
import com.googlecode.totallylazy.proxy.Invocation;
import com.googlecode.utterlyidle.MediaType;
import com.googlecode.utterlyidle.Redirector;
import com.googlecode.utterlyidle.Response;
import com.googlecode.utterlyidle.Status;
import com.googlecode.utterlyidle.annotations.FormParam;
import com.googlecode.utterlyidle.annotations.GET;
import com.googlecode.utterlyidle.annotations.POST;
import com.googlecode.utterlyidle.annotations.Path;
import com.googlecode.utterlyidle.annotations.Produces;
import com.googlecode.utterlyidle.annotations.QueryParam;

import java.io.PrintStream;
import java.util.List;
import java.util.UUID;

import static com.googlecode.barongreenback.shared.ModelRepository.MODEL_TYPE;
import static com.googlecode.barongreenback.shared.RecordDefinition.convert;
import static com.googlecode.funclate.Model.model;
import static com.googlecode.lazyrecords.Using.using;
import static com.googlecode.totallylazy.Pair.pair;
import static com.googlecode.totallylazy.Predicates.is;
import static com.googlecode.totallylazy.Predicates.where;
import static com.googlecode.totallylazy.Uri.uri;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.utterlyidle.ResponseBuilder.response;
import static com.googlecode.utterlyidle.annotations.AnnotatedBindings.relativeUriOf;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;

@Path("crawler")
@Produces(MediaType.TEXT_HTML)
public class CrawlerResource {
    private final ModelRepository modelRepository;
    private final Redirector redirector;
    private final CrawlInterval interval;
    private final Crawler crawler;

    public CrawlerResource(final ModelRepository modelRepository, Redirector redirector, CrawlInterval interval, Crawler crawler) {
        this.interval = interval;
        this.modelRepository = modelRepository;
        this.redirector = redirector;
        this.crawler = crawler;
    }

    @GET
    @Path("list")
    public Model list() {
        List<Model> models = allCrawlerModels().map(asModelWithId()).toList();
        return model().add("items", models).add("anyExists", !models.isEmpty());
    }

    @GET
    @Path("export")
    @Produces("application/json")
    public String export(@QueryParam("id") UUID id) {
        return modelFor(id).toString();
    }

    @GET
    @Path("import")
    public Model importForm() {
        return model();
    }

    @POST
    @Path("import")
    public Response importJson(@FormParam("model") String model, @FormParam("id") Option<UUID> id) {
        modelRepository.set(id.getOrElse(randomUUID()), Model.parse(model));
        return redirectToCrawlerList();
    }

    @POST
    @Path("delete")
    public Response delete(@FormParam("id") UUID id) {
        modelRepository.remove(id);
        return redirectToCrawlerList();
    }

    @POST
    @Path("reset")
    public Response reset(@FormParam("id") UUID id) {
        Model model = modelRepository.get(id).get();
        Model form = model.get("form", Model.class);
        form.remove("checkpoint", String.class);
        form.add("checkpoint", "");
        form.remove("checkpointType", String.class);
        form.add("checkpointType", String.class.getName());
        modelRepository.set(id, model);
        return redirectToCrawlerList();
    }

    @GET
    @Path("new")
    public Model newForm() {
        return Forms.emptyForm(Forms.NUMBER_OF_FIELDS);
    }

    @POST
    @Path("new")
    public Response newCrawler(Model model) throws Exception {
        return edit(randomUUID(), model);
    }

    @GET
    @Path("exists")
    @Produces(MediaType.TEXT_PLAIN)
    public boolean exists(@QueryParam("id") UUID id) {
        return !modelRepository.get(id).isEmpty();
    }

    @GET
    @Path("edit")
    public Model edit(@QueryParam("id") UUID id) {
        return Forms.addTemplates(modelFor(id));
    }

    @POST
    @Path("edit")
    public Response edit(@QueryParam("id") UUID id, final Model root) throws Exception {
        Model form = root.get("form", Model.class);
        String from = form.get("from", String.class);
        String update = form.get("update", String.class);
        String more = form.get("more", String.class);
        String checkpoint = form.get("checkpoint", String.class);
        String checkpointType = form.get("checkpointType", String.class);
        Model record = form.get("record", Model.class);
        RecordDefinition recordDefinition = convert(record);
        modelRepository.set(id, Forms.crawler(update, from, more, checkpoint, checkpointType, recordDefinition.toModel()));
        return redirectToCrawlerList();
    }

    @POST
    @Path("crawl")
    @Produces(MediaType.TEXT_PLAIN)
    public Response crawl(@FormParam("id") final UUID id) throws Exception {
        PrintStream log = new StringPrintStream();
        return numberOfRecordsUpdated(crawler.crawl(id, log), log);
    }


    private Sequence<Pair<UUID, Model>> allCrawlerModels() {
        return modelRepository.find(where(MODEL_TYPE, is("form")));
    }

    private Model modelFor(UUID id) {
        return modelRepository.get(id).get();
    }

    private Callable1<? super Pair<UUID, Model>, Model> asModelWithId() {
        return new Callable1<Pair<UUID, Model>, Model>() {
            public Model call(Pair<UUID, Model> pair) throws Exception {
                return model().
                        add("id", pair.first().toString()).
                        add("model", pair.second()).
                        add("jobUrl", jobUrl(pair.first())).
                        add("resettable", hasCheckpoint(pair.second()));
            }
        };
    }

    private boolean hasCheckpoint(Model model) {
        return !Strings.isEmpty(model.get("form", Model.class).get("checkpoint", String.class));
    }

    private Uri jobUrl(UUID uuid) throws Exception {
        Uri scheduled = scheduleAQueuedCrawl(null, uuid, interval.value());
        return redirector.absoluteUriOf(scheduled);
    }

    public static Uri scheduleAQueuedCrawl(UUID crawlerId, UUID schedulerId, Long interval) throws Exception {
        String crawlerJob = absolutePathOf(method(on(CrawlerResource.class).crawl(crawlerId)));
        String queued = absolutePathOf(method(on(QueuesResource.class).queue(null, crawlerJob)));
        return relativeUriOf(method(on(JobsResource.class).schedule(schedulerId, interval, queued)));
    }

    private static String absolutePathOf(Invocation<?, ?> method) {
        return "/" + relativeUriOf(method);
    }

    private Response redirectToCrawlerList() {
        return redirector.seeOther(method(on(getClass()).list()));
    }


    private Response numberOfRecordsUpdated(Number updated, PrintStream log) {
        return response(Status.OK.description(format("OK - Updated %s Records", updated))).
                entity(log).
                build();
    }
}
