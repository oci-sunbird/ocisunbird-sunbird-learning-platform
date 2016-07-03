package com.ilimi.taxonomy.content.concrete.processor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ilimi.common.exception.ClientException;
import com.ilimi.common.exception.ServerException;
import com.ilimi.taxonomy.content.common.ContentErrorMessageConstants;
import com.ilimi.taxonomy.content.entity.Controller;
import com.ilimi.taxonomy.content.entity.Plugin;
import com.ilimi.taxonomy.content.enums.ContentErrorCodeConstants;
import com.ilimi.taxonomy.content.enums.ContentWorkflowPipelineParams;
import com.ilimi.taxonomy.content.processor.AbstractProcessor;

public class EmbedControllerProcessor extends AbstractProcessor {

	private static Logger LOGGER = LogManager.getLogger(EmbedControllerProcessor.class.getName());

	public EmbedControllerProcessor(String basePath, String contentId) {
		if (!isValidBasePath(basePath))
			throw new ClientException(ContentErrorCodeConstants.INVALID_PARAMETER.name(),
					ContentErrorMessageConstants.INVALID_CWP_CONST_PARAM + " | [Path does not Exist.]");
		if (StringUtils.isBlank(contentId))
			throw new ClientException(ContentErrorCodeConstants.INVALID_PARAMETER.name(),
					ContentErrorMessageConstants.INVALID_CWP_CONST_PARAM + " | [Invalid Content Id.]");
		this.basePath = basePath;
		this.contentId = contentId;
	}

	@Override
	protected Plugin process(Plugin plugin) {
		try {
			if (null != plugin)
				plugin = embedController(plugin);
		} catch(Exception e) {
			throw new ServerException(ContentErrorCodeConstants.PROCESSOR_ERROR.name(), 
					ContentErrorMessageConstants.PROCESSOR_ERROR + " | [EmbedControllerProcessor]", e);
		}
		return plugin;
	}

	private Plugin embedController(Plugin plugin) {
		try {
			if (null != plugin) {
				List<Controller> controllers = plugin.getControllers();
				if (null != controllers) {
					for (Controller controller : controllers) {
						if (StringUtils.isBlank(controller.getcData())) {
							LOGGER.info("There is No Existing In-Line Controller.");
							Map<String, String> data = controller.getData();
							if (null != data) {
								String id = data.get(ContentWorkflowPipelineParams.id.name());
								LOGGER.info("Controller Id: " + id);
								if (!StringUtils.isBlank(id)) {
									File file = new File(
											basePath + File.separator + ContentWorkflowPipelineParams.items.name()
													+ File.separator + id + ".json");
									if (file.exists()) {
										LOGGER.info("Reading Controller File: " + file.getName());
										controller.setcData(FileUtils.readFileToString(file));
									}
								}
							}
						}
					}
				}
			}
		} catch (IOException e) {
			throw new ServerException(ContentErrorCodeConstants.CONTROLLER_FILE_READ.name(),
					ContentErrorMessageConstants.CONTROLLER_FILE_READ_ERROR, e);
		}
		return plugin;
	}

}
