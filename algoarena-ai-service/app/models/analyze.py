from pydantic import BaseModel,Field

class AnalyzeRequest(BaseModel):
    problem_id:str
    source_code:str
    language:str
    verdict:str
    error_message:str = Field(default="",max_length=500)
    