package com.custom.postprocessing.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.custom.postprocessing.entity.PostProcessingJsonEntity;
import com.custom.postprocessing.scheduler.PostProcessingScheduler;

/**
 * @author kumar.charanswain
 *
 */
@RequestMapping("postprocessing")
@RestController
public class PostProcessingController {

    @Autowired
    private PostProcessingScheduler postProcessingScheduler;

    @GetMapping("formoperation")
    public PostProcessingJsonEntity  manualPostProcessBatch() {

        return postProcessingScheduler.smartCommPostProcessing();
    }
}
