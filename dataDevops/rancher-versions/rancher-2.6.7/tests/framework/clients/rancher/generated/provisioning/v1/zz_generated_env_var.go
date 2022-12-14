package client

const (
	EnvVarType       = "envVar"
	EnvVarFieldName  = "name"
	EnvVarFieldValue = "value"
)

type EnvVar struct {
	Name  string `json:"name,omitempty" yaml:"name,omitempty"`
	Value string `json:"value,omitempty" yaml:"value,omitempty"`
}
