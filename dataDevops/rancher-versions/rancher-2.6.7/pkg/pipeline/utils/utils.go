package utils

import (
	"encoding/json"
	"fmt"
	"reflect"
	"strconv"
	"strings"
	"time"

	"github.com/mitchellh/mapstructure"
	"github.com/pkg/errors"
	v32 "github.com/rancher/rancher/pkg/apis/project.cattle.io/v3"
	v3 "github.com/rancher/rancher/pkg/generated/norman/project.cattle.io/v3"
	"github.com/rancher/rancher/pkg/pipeline/remote/model"
	"github.com/rancher/rancher/pkg/ref"
	"github.com/sirupsen/logrus"
	"golang.org/x/crypto/bcrypt"
	"gopkg.in/yaml.v2"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

func initExecution(p *v3.Pipeline, config *v32.PipelineConfig) *v3.PipelineExecution {
	//add Clone stage/step at the start

	toRunConfig := configWithCloneStage(config)
	execution := &v3.PipelineExecution{
		ObjectMeta: metav1.ObjectMeta{
			Name:      GetNextExecutionName(p),
			Namespace: p.Namespace,
			Labels:    map[string]string{PipelineFinishLabel: ""},
		},
		Spec: v32.PipelineExecutionSpec{
			ProjectName:    p.Spec.ProjectName,
			PipelineName:   p.Namespace + ":" + p.Name,
			RepositoryURL:  p.Spec.RepositoryURL,
			Run:            p.Status.NextRun,
			PipelineConfig: *toRunConfig,
		},
	}
	execution.Status.ExecutionState = StateWaiting
	execution.Status.Started = time.Now().Format(time.RFC3339)
	execution.Status.Stages = make([]v32.StageStatus, len(toRunConfig.Stages))

	for i := 0; i < len(execution.Status.Stages); i++ {
		stage := &execution.Status.Stages[i]
		stage.State = StateWaiting
		stepsize := len(toRunConfig.Stages[i].Steps)
		stage.Steps = make([]v32.StepStatus, stepsize)
		for j := 0; j < stepsize; j++ {
			step := &stage.Steps[j]
			step.State = StateWaiting
		}
	}
	return execution
}

func configWithCloneStage(config *v32.PipelineConfig) *v32.PipelineConfig {
	result := config.DeepCopy()
	if len(config.Stages) > 0 && len(config.Stages[0].Steps) > 0 &&
		config.Stages[0].Steps[0].SourceCodeConfig != nil {
		return result
	}
	cloneStage := v32.Stage{
		Name:  "Clone",
		Steps: []v32.Step{{SourceCodeConfig: &v32.SourceCodeConfig{}}},
	}
	result.Stages = append([]v32.Stage{cloneStage}, result.Stages...)
	return result
}

func GetNextExecutionName(p *v3.Pipeline) string {
	if p == nil {
		return ""
	}
	return fmt.Sprintf("%s-%d", p.Name, p.Status.NextRun)
}

func IsStageSuccess(stage v32.StageStatus) bool {
	if stage.State == StateSuccess {
		return true
	} else if stage.State == StateFailed || stage.State == StateDenied {
		return false
	}
	successSteps := 0
	for _, step := range stage.Steps {
		if step.State == StateSuccess || step.State == StateSkipped {
			successSteps++
		}
	}
	return successSteps == len(stage.Steps)
}

func IsFinishState(state string) bool {
	if state == StateBuilding ||
		state == StateWaiting ||
		state == StateQueueing ||
		state == StatePending {
		return false
	}
	return true
}

func GenerateExecution(executions v3.PipelineExecutionInterface, pipeline *v3.Pipeline, pipelineConfig *v32.PipelineConfig, info *model.BuildInfo) (*v3.PipelineExecution, error) {

	//Generate a new pipeline execution
	execution := initExecution(pipeline, pipelineConfig)
	execution.Spec.TriggeredBy = info.TriggerType
	execution.Spec.TriggerUserName = info.TriggerUserName
	execution.Spec.Branch = info.Branch
	execution.Spec.Author = info.Author
	execution.Spec.AvatarURL = info.AvatarURL
	execution.Spec.Email = info.Email
	execution.Spec.Message = info.Message
	execution.Spec.HTMLLink = info.HTMLLink
	execution.Spec.Title = info.Title
	execution.Spec.Ref = info.Ref
	execution.Spec.Commit = info.Commit
	execution.Spec.Event = info.Event

	if info.RepositoryURL != "" {
		execution.Spec.RepositoryURL = info.RepositoryURL
	}

	if !Match(execution.Spec.PipelineConfig.Branch, info.Branch) {
		logrus.Debug("conditions do not match")
		return nil, nil
	}

	execution, err := executions.Create(execution)
	if err != nil {
		return nil, err
	}
	return execution, nil
}

func SplitImageTag(image string) (string, string, string) {
	registry, repo, tag := "", "", ""
	i := strings.Index(image, "/")
	if i == -1 || (!strings.ContainsAny(image[:i], ".:") && image[:i] != "localhost") {
		registry = DefaultRegistry
	} else {
		registry = image[:i]
		image = image[i+1:]
	}
	i = strings.Index(image, ":")
	if i == -1 {
		repo = image
		tag = DefaultTag
	} else {
		repo = image[:i]
		tag = image[i+1:]
	}
	return registry, repo, tag
}

func ValidPipelineConfig(config v32.PipelineConfig) error {
	if len(config.Stages) < 1 ||
		len(config.Stages[0].Steps) < 1 ||
		config.Stages[0].Steps[0].SourceCodeConfig == nil {
		return fmt.Errorf("invalid definition for pipeline: expect souce code step at the start")
	}
	return nil
}

func GetPipelineCommonName(projectName string) string {
	_, p := ref.Parse(projectName)
	return p + PipelineNamespaceSuffix
}

func GetEnvVarMap(execution *v3.PipelineExecution) map[string]string {

	m := map[string]string{}
	repoURL := execution.Spec.RepositoryURL
	repoName := ""
	if strings.Contains(repoURL, "/") {
		trimmedURL := strings.TrimRight(repoURL, "/")
		idx := strings.LastIndex(trimmedURL, "/")
		repoName = strings.TrimSuffix(trimmedURL[idx+1:], ".git")
	}

	commit := execution.Spec.Commit
	if commit != "" && len(commit) > 7 {
		//use abbreviated SHA
		commit = commit[:7]
	}
	_, pipelineID := ref.Parse(execution.Spec.PipelineName)
	clusterID, projectID := ref.Parse(execution.Spec.ProjectName)

	localRegistry := ""
	if execution.Annotations != nil && execution.Annotations[LocalRegistryPortLabel] != "" {
		localRegistry = "127.0.0.1:" + execution.Annotations[LocalRegistryPortLabel]
	}

	m[EnvGitCommit] = commit
	m[EnvGitRepoName] = repoName
	m[EnvGitRef] = execution.Spec.Ref
	m[EnvGitBranch] = execution.Spec.Branch
	m[EnvGitURL] = execution.Spec.RepositoryURL
	m[EnvPipelineID] = pipelineID
	m[EnvTriggerType] = execution.Spec.TriggeredBy
	m[EnvEvent] = execution.Spec.Event
	m[EnvExecutionID] = execution.Name
	m[EnvExecutionSequence] = strconv.Itoa(execution.Spec.Run)
	m[EnvProjectID] = projectID
	m[EnvClusterID] = clusterID
	m[EnvLocalRegistry] = localRegistry

	if execution.Spec.Event == WebhookEventTag {
		m[EnvGitTag] = strings.TrimPrefix(execution.Spec.Ref, "refs/tags/")
	}

	return m
}

func PipelineConfigToYaml(pipelineConfig *v32.PipelineConfig) ([]byte, error) {

	content, err := yaml.Marshal(pipelineConfig)
	if err != nil {
		return nil, err
	}

	return content, nil
}

func PipelineConfigFromYaml(content []byte) (*v32.PipelineConfig, error) {

	out := &v32.PipelineConfig{}
	err := yaml.Unmarshal(content, out)
	if err != nil {
		return nil, errors.Wrapf(err, "Failed to parse the pipeline file")
	}

	return out, nil
}

func BCryptHash(password string) (string, error) {
	passwordBytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(passwordBytes), nil
}

func GetRegistryPortMapping(cm *corev1.ConfigMap) (map[string]string, error) {
	portMap := map[string]string{}
	yamlMap := map[string]string{}
	curYaml := cm.Data[RegistryPortMappingFile]
	if err := yaml.Unmarshal([]byte(curYaml), &yamlMap); err != nil {
		return nil, err
	}
	if err := json.Unmarshal([]byte(yamlMap[RegistryPortMappingKey]), &portMap); err != nil {
		return nil, err
	}
	return portMap, nil
}

func SetRegistryPortMapping(configmap *corev1.ConfigMap, portMap map[string]string) error {
	yamlMap := map[string]string{}
	b, err := json.Marshal(portMap)
	if err != nil {
		return err
	}
	yamlMap[RegistryPortMappingKey] = string(b)
	b, err = yaml.Marshal(yamlMap)
	if err != nil {
		return err
	}
	configmap.Data[RegistryPortMappingFile] = string(b)
	return nil
}

// EnsureAccessToken Checks expiry and do token refresh when needed
func EnsureAccessToken(credentialInterface v3.SourceCodeCredentialInterface, remote model.Remote, credential *v3.SourceCodeCredential) (string, error) {
	if credential == nil {
		return "", nil
	}
	refresher, ok := remote.(model.Refresher)
	if !ok {
		return credential.Spec.AccessToken, nil
	}

	t, err := time.Parse(time.RFC3339, credential.Spec.Expiry)
	if err != nil {
		return "", err
	}
	if t.Before(time.Now().Add(time.Minute)) {
		torefresh := credential.DeepCopy()
		ok, err := refresher.Refresh(torefresh)
		if err != nil {
			return "", err
		}
		if ok {
			if _, err := credentialInterface.Update(torefresh); err != nil {
				return "", err
			}
		}
		return torefresh.Spec.AccessToken, nil
	}
	return credential.Spec.AccessToken, nil
}

func ObjectMetaFromUnstructureContent(unstructuredContent map[string]interface{}) (*metav1.ObjectMeta, error) {
	metadataMap, ok := unstructuredContent["metadata"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("failed to retrieve metadata, cannot read k8s unstructured data")
	}

	objectMeta := &metav1.ObjectMeta{}
	stringToTimeHook := func(f reflect.Type, t reflect.Type, data interface{}) (interface{}, error) {
		if f.Kind() == reflect.String && t == reflect.TypeOf(metav1.Time{}) {
			time, err := time.Parse(time.RFC3339, data.(string))
			return metav1.Time{Time: time}, err
		}
		return data, nil
	}
	decoder, err := mapstructure.NewDecoder(&mapstructure.DecoderConfig{
		DecodeHook: stringToTimeHook,
		Result:     objectMeta,
	})
	if err != nil {
		return nil, err
	}
	if err := decoder.Decode(metadataMap); err != nil {
		return nil, fmt.Errorf("failed to decode metadata, error: %v", err)
	}
	return objectMeta, nil
}
