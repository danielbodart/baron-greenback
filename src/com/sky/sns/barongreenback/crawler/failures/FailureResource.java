package com.sky.sns.barongreenback.crawler.failures;

import com.sky.sns.barongreenback.crawler.CheckpointHandler;
import com.sky.sns.barongreenback.crawler.CheckpointUpdater;
import com.sky.sns.barongreenback.crawler.CrawlerRepository;
import com.sky.sns.barongreenback.crawler.CrawlerScope;
import com.sky.sns.barongreenback.crawler.HttpJobExecutor;
import com.sky.sns.barongreenback.crawler.jobs.Job;
import com.sky.sns.barongreenback.shared.pager.Pager;
import com.sky.sns.barongreenback.shared.sorter.Sorter;
import com.googlecode.funclate.Model;
import com.googlecode.lazyrecords.Keyword;
import com.googlecode.lazyrecords.Record;
import com.googlecode.totallylazy.Block;
import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Callable2;
import com.googlecode.totallylazy.Callables;
import com.googlecode.totallylazy.Option;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Sequences;
import com.googlecode.utterlyidle.Redirector;
import com.googlecode.utterlyidle.Response;
import com.googlecode.utterlyidle.Status;
import com.googlecode.utterlyidle.annotations.FormParam;
import com.googlecode.utterlyidle.annotations.GET;
import com.googlecode.utterlyidle.annotations.POST;
import com.googlecode.utterlyidle.annotations.Path;
import com.googlecode.utterlyidle.annotations.QueryParam;
import com.googlecode.yadic.Container;

import java.util.UUID;

import static com.sky.sns.barongreenback.crawler.failures.FailureRepository.DURATION;
import static com.sky.sns.barongreenback.crawler.failures.FailureRepository.ID;
import static com.sky.sns.barongreenback.crawler.failures.FailureRepository.REASON;
import static com.sky.sns.barongreenback.crawler.failures.FailureRepository.REQUEST_TIME;
import static com.sky.sns.barongreenback.crawler.failures.FailureRepository.URI;
import static com.sky.sns.barongreenback.shared.sorter.Sorter.sortKeywordFromRequest;
import static com.googlecode.funclate.Model.mutable.model;
import static com.googlecode.totallylazy.Option.some;
import static com.googlecode.totallylazy.Predicates.all;
import static com.googlecode.totallylazy.proxy.Call.method;
import static com.googlecode.totallylazy.proxy.Call.on;
import static com.googlecode.utterlyidle.Responses.response;

@Path("/crawler/failures")
public class FailureResource {
    private final Failures failures;
    private final Redirector redirector;
    private final CrawlerRepository crawlerRepository;
    private final Container requestScope;
    private final Pager pager;
    private final Sorter sorter;
    private final FailureRepository failureRepository;

    public FailureResource(Failures failures, FailureRepository failureRepository, Redirector redirector, CrawlerRepository crawlerRepository, Container requestScope, Pager pager, Sorter sorter) {
        this.failures = failures;
        this.redirector = redirector;
        this.crawlerRepository = crawlerRepository;
        this.requestScope = requestScope;
        this.pager = pager;
        this.sorter = sorter;
        this.failureRepository = failureRepository;
    }

    @GET
    @Path("list")
    public Model list(@QueryParam("message") Option<String> message) {
        Sequence<Keyword<?>> headers = Sequences.<Keyword<?>>sequence(URI, REASON, REQUEST_TIME, DURATION);
        Sequence<Record> unpaged = failureRepository.find(all());
        Sequence<Record> sorted = sorter.sort(unpaged, sortKeywordFromRequest(headers));
        Sequence<Record> paged = pager.paginate(sorted);
        Model model = pager.model(sorter.model(model().
                add("items", paged.map(toModel()).toList()), headers, paged));
        return message.fold(model, toMessageModel()).
                add("retryUrl", redirector.absoluteUriOf(method(on(FailureResource.class).retry(null)))).
                add("deleteUrl", redirector.absoluteUriOf(method(on(FailureResource.class).delete(null)))).
                add("retryAll", redirector.absoluteUriOf(method(on(FailureResource.class).retryAll()))).
                add("deleteAll", redirector.absoluteUriOf(method(on(FailureResource.class).deleteAll())));
    }

    private Callable2<Model, String, Model> toMessageModel() {
        return new Callable2<Model, String, Model>() {
            @Override
            public Model call(Model model, String text) throws Exception {
                return model.add("message", model().add("text", text).add("category", "success"));
            }
        };
    }

    @POST
    @Path("retry")
    public Response retry(@FormParam("id") UUID id) {
        return failures.get(id).map(toRetry(id)).getOrElse(response(Status.NOT_FOUND));
    }

    @POST
    @Path("delete")
    public Response delete(@FormParam("id") UUID id) {
        return failures.get(id).map(toDelete(id)).getOrElse(response(Status.NOT_FOUND));
    }

    @POST
    @Path("retryAll")
    public Response retryAll() {
        Sequence<UUID> uuids = failures.values().map(Callables.<UUID>first());
        int rowsToDelete = uuids.size();
        uuids.each(retry());
        return backToMe(rowsToDelete + " failures have been added to the job queue");
    }

    @POST
    @Path("deleteAll")
    public Response deleteAll() {
        return backToMe(failures.removeAll() + " failures(s) have been deleted");
    }

    private Block<UUID> ignore() {
        return new Block<UUID>() {
            @Override
            protected void execute(UUID uuid) throws Exception {
                delete(uuid);
            }
        };
    }

    private Block<UUID> retry() {
        return new Block<UUID>() {
            @Override
            protected void execute(UUID uuid) throws Exception {
                retry(uuid);
            }
        };
    }


    private Callable1<Failure, Response> toDelete(final UUID id) {
        return new Callable1<Failure, Response>() {
            @Override
            public Response call(Failure stagedJobResponsePair) throws Exception {
                failures.delete(id);
                return backToMe("Job deleted");
            }
        };
    }

    private Callable1<Failure, Response> toRetry(final UUID id) {
        return new Callable1<Failure, Response>() {
            @Override
            public Response call(Failure failure) throws Exception {
                executor(failure.job()).execute(failure.job());
                failures.delete(id);
                return backToMe("Job retried");
            }
        };
    }

    private Response backToMe(String message) {
        return redirector.seeOther(method(on(FailureResource.class).list(some(message))));
    }

    private Callable1<Record, Model> toModel() {
        return new Callable1<Record, Model>() {
            @Override
            public Model call(Record record) throws Exception {
                return model().
                        add("uri", record.get(URI)).
                        add("reason", record.get(REASON)).
                        add("requestTime", record.get(REQUEST_TIME)).
                        add("duration", record.get(DURATION)).
                        add("id", record.get(ID));
            }
        };
    }

    private HttpJobExecutor executor(Job job) {
        CrawlerScope crawlerScope = CrawlerScope.crawlerScope(requestScope,
                new CheckpointUpdater(requestScope.get(CheckpointHandler.class), job.crawlerId(),
                        crawlerRepository.modelFor(job.crawlerId()).get()));
        return crawlerScope.get(HttpJobExecutor.class);
    }
}