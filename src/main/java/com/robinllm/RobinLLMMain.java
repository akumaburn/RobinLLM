package com.robinllm;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusMain
public class RobinLLMMain {
    private static final Logger LOG = LoggerFactory.getLogger(RobinLLMMain.class);

    public static void main(String[] args) {
        LOG.info("Starting Robin LLM - Intelligent LLM Routing Service");
        Quarkus.run(args);
    }
}
